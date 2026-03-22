package com.infosung.atomic.starter.autoconfigure.idempotency

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.idempotency.DefaultIdempotencyFingerprintResolver
import com.infosung.atomic.spring.idempotency.IdempotencyFilter
import com.infosung.atomic.spring.idempotency.IdempotencyFingerprintResolver
import com.infosung.atomic.spring.idempotency.IdempotencyStore
import com.infosung.atomic.spring.idempotency.InMemoryIdempotencyStore
import java.util.Locale
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper

/** Auto-configuration for HTTP idempotency filter and default in-memory store. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.spring.idempotency.IdempotencyFilter",
            "jakarta.servlet.Filter",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.idempotency",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicIdempotencyProperties::class)
class AtomicIdempotencyAutoConfiguration {
  /** Validates idempotency properties on startup when feature is enabled. */
  @Bean
  fun idempotencyPropertiesValidation(
      properties: AtomicIdempotencyProperties,
  ): Any {
    properties.validate()
    return IdempotencyPropertiesValidation
  }

  /** Registers in-memory idempotency store when no custom store bean exists. */
  @Bean
  @ConditionalOnMissingBean
  fun idempotencyStore(
      properties: AtomicIdempotencyProperties,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): IdempotencyStore {
    properties.validate()
    val timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() }
    return InMemoryIdempotencyStore(
        cleanupInterval = properties.inMemory.cleanupInterval,
        nowProvider = timeProvider::nowMillis,
    )
  }

  /** Registers default request fingerprint resolver. */
  @Bean
  @ConditionalOnMissingBean
  fun idempotencyFingerprintResolver(): IdempotencyFingerprintResolver {
    return DefaultIdempotencyFingerprintResolver()
  }

  /** Registers [IdempotencyFilter]. */
  @Bean
  @ConditionalOnMissingBean
  fun idempotencyFilter(
      properties: AtomicIdempotencyProperties,
      store: IdempotencyStore,
      fingerprintResolver: IdempotencyFingerprintResolver,
      objectMapperProvider: ObjectProvider<ObjectMapper>,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): IdempotencyFilter {
    properties.validate()
    val methods =
        properties.includeMethods
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()
    return IdempotencyFilter(
        store = store,
        fingerprintResolver = fingerprintResolver,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
        headerName = properties.headerName,
        ttlSeconds = properties.ttlSeconds,
        processingTtlSeconds = properties.processingTtlSeconds,
        requireHeader = properties.requireHeader,
        includeMethods = methods,
        failOpen = properties.failOpen,
        replayHeaderName = properties.replayHeaderName,
        replayBodyOmittedHeaderName = properties.replayBodyOmittedHeaderName,
        maxCachedBodyBytes = properties.maxCachedBodyBytes,
        objectMapper = objectMapperProvider.getIfAvailable { ObjectMapper() },
    )
  }

  /** Registers servlet filter for idempotency enforcement. */
  @Bean
  @ConditionalOnMissingBean(name = ["idempotencyFilterRegistration"])
  @ConditionalOnProperty(
      prefix = "atomic.idempotency.filter",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun idempotencyFilterRegistration(
      filter: IdempotencyFilter,
      properties: AtomicIdempotencyProperties,
  ): FilterRegistrationBean<IdempotencyFilter> {
    properties.validate()
    val patterns =
        properties.filter.urlPatterns
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("/*") }
    return FilterRegistrationBean(filter).apply {
      order = properties.filter.order
      addUrlPatterns(*patterns.toTypedArray())
    }
  }

  private object IdempotencyPropertiesValidation
}
