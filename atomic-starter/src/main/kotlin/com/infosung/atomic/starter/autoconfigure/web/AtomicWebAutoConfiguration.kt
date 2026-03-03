package com.infosung.atomic.starter.autoconfigure.web

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.json.JsonTransfer
import com.infosung.atomic.spring.web.log.ApiLogFilter
import com.infosung.atomic.spring.web.log.LogSaver
import com.infosung.atomic.spring.web.log.ServiceLogger
import com.infosung.atomic.spring.web.ratelimit.HeaderRateLimitKeyResolver
import com.infosung.atomic.spring.web.ratelimit.InMemoryRateLimitStore
import com.infosung.atomic.spring.web.ratelimit.IpRateLimitKeyResolver
import com.infosung.atomic.spring.web.ratelimit.PathPrefixRateLimitPolicyResolver
import com.infosung.atomic.spring.web.ratelimit.RateLimitFilter
import com.infosung.atomic.spring.web.ratelimit.RateLimitKeyResolver
import com.infosung.atomic.spring.web.ratelimit.RateLimitMissingKeyPolicy
import com.infosung.atomic.spring.web.ratelimit.RateLimitPathKeyStrategy
import com.infosung.atomic.spring.web.ratelimit.RateLimitPolicy
import com.infosung.atomic.spring.web.ratelimit.RateLimitPolicyResolver
import com.infosung.atomic.spring.web.ratelimit.RateLimitRule
import com.infosung.atomic.spring.web.ratelimit.RateLimitStore
import java.util.Locale
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.util.ClassUtils

/** Auto-configuration for atomic spring-web helper beans. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.spring.web.json.JsonTransfer",
            "com.infosung.atomic.spring.web.log.ServiceLogger",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.web", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AtomicWebProperties::class)
class AtomicWebAutoConfiguration {
  /** Validates rate-limit properties on startup when feature is enabled. */
  @Bean
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitPropertiesValidation(properties: AtomicWebProperties): Any {
    properties.rateLimit.validate()
    return RateLimitPropertiesValidation
  }

  /** Registers [JsonTransfer] with optional sensitive-key pattern override. */
  @Bean
  @ConditionalOnMissingBean
  fun jsonTransfer(properties: AtomicWebProperties): JsonTransfer {
    val jsonTransfer = JsonTransfer()
    val pattern = properties.json.sensitiveKeyPattern?.takeIf { it.isNotBlank() }
    if (pattern != null) {
      jsonTransfer.configureSensitiveKeyRegex(pattern)
    }
    return jsonTransfer
  }

  /** Registers [ServiceLogger] when [LogSaver] is present. */
  @Bean
  @ConditionalOnBean(LogSaver::class)
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.logging",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun serviceLogger(
      logSaver: LogSaver,
      properties: AtomicWebProperties,
  ): ServiceLogger = ServiceLogger(logSaver = logSaver, maxQueueSize = properties.logging.queueSize)

  /** Registers [ApiLogFilter] when [ServiceLogger] is available. */
  @Bean
  @ConditionalOnBean(ServiceLogger::class)
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.logging",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun apiLogFilter(
      serviceLogger: ServiceLogger,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): ApiLogFilter =
      ApiLogFilter(
          logger = serviceLogger,
          timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() })

  /** Registers servlet filter for API response logging. */
  @Bean
  @ConditionalOnBean(ApiLogFilter::class)
  @ConditionalOnMissingBean(name = ["apiLogFilterRegistration"])
  @ConditionalOnProperty(
      prefix = "atomic.web.logging.filter",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun apiLogFilterRegistration(
      apiLogFilter: ApiLogFilter,
      properties: AtomicWebProperties,
  ): FilterRegistrationBean<ApiLogFilter> {
    val patterns = properties.logging.filter.urlPatterns.ifEmpty { mutableListOf("/*") }
    return FilterRegistrationBean(apiLogFilter).apply {
      order = properties.logging.filter.order
      addUrlPatterns(*patterns.toTypedArray())
    }
  }

  /** Registers [RateLimitStore] by configured type and available dependencies. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitStore(
      properties: AtomicWebProperties,
      beanFactory: BeanFactory,
  ): RateLimitStore {
    val rateLimit = properties.rateLimit
    rateLimit.validate()
    val inMemoryStore = InMemoryRateLimitStore(cleanupInterval = rateLimit.inMemory.cleanupInterval)
    val redisTemplate = resolveRedisTemplate(beanFactory)
    return when (rateLimit.store) {
      AtomicWebProperties.StoreType.IN_MEMORY -> inMemoryStore

      AtomicWebProperties.StoreType.REDIS -> {
        val template =
            redisTemplate
                ?: throw IllegalStateException(
                    "atomic.web.rate-limit.store=REDIS requires StringRedisTemplate bean.")
        newRedisRateLimitStore(
            redisTemplate = template,
            keyPrefix = rateLimit.redis.keyPrefix,
        )
      }

      AtomicWebProperties.StoreType.CUSTOM -> {
        throw IllegalStateException(
            "atomic.web.rate-limit.store=CUSTOM requires custom RateLimitStore bean.")
      }

      AtomicWebProperties.StoreType.AUTO -> {
        if (redisTemplate != null) {
          newRedisRateLimitStore(
              redisTemplate = redisTemplate,
              keyPrefix = rateLimit.redis.keyPrefix,
          )
        } else {
          inMemoryStore
        }
      }
    }
  }

  /** Registers default policy resolver from `atomic.web.rate-limit.*` properties. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitPolicyResolver(properties: AtomicWebProperties): RateLimitPolicyResolver {
    val rateLimit = properties.rateLimit
    rateLimit.validate()
    val defaultPolicy =
        RateLimitPolicy(limit = rateLimit.limit, windowSeconds = rateLimit.windowSeconds)
    val rules =
        rateLimit.rules.map { rule ->
          val methods =
              rule.methods
                  .map { it.trim().uppercase(Locale.ROOT) }
                  .filter { it.isNotBlank() }
                  .toSet()
          RateLimitRule(
              pathPrefix = rule.pathPrefix?.trim()?.takeIf { it.isNotBlank() },
              methods = methods,
              policy =
                  RateLimitPolicy(
                      limit = rule.limit ?: rateLimit.limit,
                      windowSeconds = rule.windowSeconds ?: rateLimit.windowSeconds,
                  ),
          )
        }
    val excluded =
        rateLimit.excludePathPrefixes.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    return PathPrefixRateLimitPolicyResolver(
        defaultPolicy = defaultPolicy,
        rules = rules,
        excludedPathPrefixes = excluded,
        pathKeyStrategy =
            when (rateLimit.pathKeyStrategy) {
              AtomicWebProperties.PathKeyStrategy.RULE_PREFIX -> {
                RateLimitPathKeyStrategy.RULE_PREFIX
              }

              AtomicWebProperties.PathKeyStrategy.REQUEST_URI -> {
                RateLimitPathKeyStrategy.REQUEST_URI
              }
            },
    )
  }

  /** Registers key resolver by configured key strategy. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitKeyResolver(properties: AtomicWebProperties): RateLimitKeyResolver {
    val rateLimit = properties.rateLimit
    rateLimit.validate()
    return when (rateLimit.keyStrategy) {
      AtomicWebProperties.KeyStrategy.IP ->
          IpRateLimitKeyResolver(
              trustForwardedHeaders = rateLimit.ip.trustForwardedHeaders,
          )
      AtomicWebProperties.KeyStrategy.HEADER -> HeaderRateLimitKeyResolver(rateLimit.keyHeaderName)
    }
  }

  /** Registers [RateLimitFilter]. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitFilter(
      properties: AtomicWebProperties,
      store: RateLimitStore,
      policyResolver: RateLimitPolicyResolver,
      keyResolver: RateLimitKeyResolver,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): RateLimitFilter {
    val rateLimit = properties.rateLimit
    rateLimit.validate()
    val includeMethods =
        rateLimit.includeMethods
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
    return RateLimitFilter(
        store = store,
        policyResolver = policyResolver,
        keyResolver = keyResolver,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
        failOpen = rateLimit.failOpen,
        missingKeyPolicy =
            when (rateLimit.missingKeyPolicy) {
              AtomicWebProperties.MissingKeyPolicy.REJECT -> RateLimitMissingKeyPolicy.REJECT
              AtomicWebProperties.MissingKeyPolicy.SKIP -> RateLimitMissingKeyPolicy.SKIP
            },
        responseBody = rateLimit.responseBody,
        includeMethods = includeMethods,
    )
  }

  /** Registers servlet filter for request rate limiting. */
  @Bean
  @ConditionalOnBean(RateLimitFilter::class)
  @ConditionalOnMissingBean(name = ["rateLimitFilterRegistration"])
  @ConditionalOnProperty(
      prefix = "atomic.web.rate-limit",
      name = ["enabled"],
      havingValue = "true",
  )
  fun rateLimitFilterRegistration(
      rateLimitFilter: RateLimitFilter,
      properties: AtomicWebProperties,
  ): FilterRegistrationBean<RateLimitFilter> {
    properties.rateLimit.validate()
    val patterns =
        properties.rateLimit.filter.urlPatterns
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("/*") }
    return FilterRegistrationBean(rateLimitFilter).apply {
      order = properties.rateLimit.filter.order
      addUrlPatterns(*patterns.toTypedArray())
    }
  }

  private object RateLimitPropertiesValidation

  private fun resolveRedisTemplate(beanFactory: BeanFactory): Any? {
    val listable = beanFactory as? ListableBeanFactory ?: return null
    val className = "org.springframework.data.redis.core.StringRedisTemplate"
    if (!ClassUtils.isPresent(className, this::class.java.classLoader)) {
      return null
    }
    val type = ClassUtils.resolveClassName(className, this::class.java.classLoader)
    return listable.getBeanProvider(type).ifAvailable
  }

  private fun newRedisRateLimitStore(
      redisTemplate: Any,
      keyPrefix: String,
  ): RateLimitStore {
    val classLoader = this::class.java.classLoader
    val templateType =
        ClassUtils.resolveClassName(
            "org.springframework.data.redis.core.StringRedisTemplate", classLoader)
    val storeType =
        ClassUtils.resolveClassName(
            "com.infosung.atomic.starter.autoconfigure.web.RedisRateLimitStore",
            classLoader,
        )
    val constructor = storeType.getConstructor(templateType, String::class.java)
    return constructor.newInstance(redisTemplate, keyPrefix) as RateLimitStore
  }
}
