package com.infosung.atomic.starter.autoconfigure.web

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.json.JsonTransfer
import com.infosung.atomic.spring.web.log.ApiLogFilter
import com.infosung.atomic.spring.web.log.LogSaver
import com.infosung.atomic.spring.web.log.ServiceLogger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean

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
}
