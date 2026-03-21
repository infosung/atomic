package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.contract.time.TimeProvider
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/** Relay-store and relay use-case auto-configuration for oauth redirect flows. */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
class AtomicAppOauthRedirectRelayAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun oauthRelayCodeStore(
      properties: AtomicAppOauthRedirectProperties,
      timeProviderProvider: ObjectProvider<TimeProvider>,
      objectMapperProvider: ObjectProvider<ObjectMapper>,
      cacheManagerProvider: ObjectProvider<CacheManager>,
      dataSourceProvider: ObjectProvider<DataSource>,
      transactionManagerProvider: ObjectProvider<PlatformTransactionManager>,
  ): OauthRelayCodeStore {
    val timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() }

    return when (properties.store.type) {
      AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY -> {
        newInMemoryStore(
            properties = properties,
            timeProvider = timeProvider,
            selectionReason = "configured",
        )
      }

      AtomicAppOauthRedirectProperties.StoreType.CACHE -> {
        val cacheManager = cacheManagerProvider.getIfAvailable()
        val objectMapper = objectMapperProvider.getIfAvailable()
        val cacheTtlSeconds = properties.store.cache.ttlSeconds ?: properties.relayCodeTtlSeconds
        val cacheName = properties.store.cache.cacheName.trim()
        if (cacheManager == null || objectMapper == null) {
          fallbackOrThrow(
              properties = properties,
              timeProvider = timeProvider,
              reason =
                  "atomic.app.oauth.redirect.store.type=cache requires CacheManager and ObjectMapper beans.",
          )
        } else if (cacheName.isBlank()) {
          fallbackOrThrow(
              properties = properties,
              timeProvider = timeProvider,
              reason = "atomic.app.oauth.redirect.store.cache.cache-name must not be blank.",
          )
        } else if (cacheTtlSeconds <= 0) {
          fallbackOrThrow(
              properties = properties,
              timeProvider = timeProvider,
              reason =
                  "atomic.app.oauth.redirect.store.cache.ttl-seconds (or relay-code-ttl-seconds) must be greater than zero.",
          )
        } else if (cacheManager.getCache(cacheName) == null) {
          fallbackOrThrow(
              properties = properties,
              timeProvider = timeProvider,
              reason = "Configured cache '$cacheName' is not available from CacheManager.",
          )
        } else {
          val cache = cacheManager.getCache(cacheName)!!
          if (!CacheOauthRelayCodeStore.supportsAtomicConsume(cache)) {
            fallbackOrThrow(
                properties = properties,
                timeProvider = timeProvider,
                reason = CacheOauthRelayCodeStore.unsupportedAtomicConsume(cacheName).message!!,
            )
          } else {
            log.info(
                "Using cache-backed oauth relay store: cacheName={}, ttlSeconds={}, failFast={}, nativeCacheType={}",
                cacheName,
                cacheTtlSeconds,
                properties.store.failFast,
                cache.getNativeCache()::class.java.name,
            )
            CacheOauthRelayCodeStore(
                cacheManager = cacheManager,
                cacheName = cacheName,
                keyPrefix = properties.store.cache.keyPrefix,
                ttlSeconds = cacheTtlSeconds,
                objectMapper = objectMapper,
                timeProvider = timeProvider,
            )
          }
        }
      }

      AtomicAppOauthRedirectProperties.StoreType.ENTITY -> {
        val dataSource = dataSourceProvider.getIfAvailable()
        val transactionManager = transactionManagerProvider.getIfAvailable()
        val objectMapper = objectMapperProvider.getIfAvailable()
        if (dataSource == null || transactionManager == null || objectMapper == null) {
          fallbackOrThrow(
              properties = properties,
              timeProvider = timeProvider,
              reason =
                  "atomic.app.oauth.redirect.store.type=entity requires DataSource, PlatformTransactionManager, and ObjectMapper beans.",
          )
        } else {
          EntityOauthRelayCodeStore(
              jdbcOperations = JdbcTemplate(dataSource),
              transactionTemplate = TransactionTemplate(transactionManager),
              tableName = properties.store.entity.tableName,
              objectMapper = objectMapper,
              timeProvider = timeProvider,
          )
        }
      }
    }
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun storeOauthRelayCodePort(
      oauthRelayCodeStore: OauthRelayCodeStore,
  ): StoreOauthRelayCodePort {
    return OauthRelayCodeComposition.storeOauthRelayCodePort(oauthRelayCodeStore)
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun issueOauthRelayCodeUseCase(
      storeOauthRelayCodePort: StoreOauthRelayCodePort,
      properties: AtomicAppOauthRedirectProperties,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): IssueOauthRelayCodeUseCase {
    return OauthRelayCodeComposition.issueOauthRelayCodeUseCase(
        storeOauthRelayCodePort = storeOauthRelayCodePort,
        properties = properties,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
    )
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun consumeOauthRelayCodeUseCase(
      storeOauthRelayCodePort: StoreOauthRelayCodePort,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): ConsumeOauthRelayCodeUseCase {
    return OauthRelayCodeComposition.consumeOauthRelayCodeUseCase(
        storeOauthRelayCodePort = storeOauthRelayCodePort,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
    )
  }

  private fun newInMemoryStore(
      properties: AtomicAppOauthRedirectProperties,
      timeProvider: TimeProvider,
      selectionReason: String,
  ): OauthRelayCodeStore {
    if (properties.store.inMemory.cleanupInterval <= 0) {
      log.warn(
          "atomic.app.oauth.redirect.store.in-memory.cleanup-interval is {}. Expired entries are not cleaned up periodically.",
          properties.store.inMemory.cleanupInterval,
      )
    }
    log.info(
        "Using in-memory oauth relay store: configuredStoreType={}, selectionReason={}, cleanupInterval={}, failFast={}",
        properties.store.type,
        selectionReason,
        properties.store.inMemory.cleanupInterval,
        properties.store.failFast,
    )
    return InMemoryOauthRelayCodeStore(
        cleanupInterval = properties.store.inMemory.cleanupInterval,
        timeProvider = timeProvider,
    )
  }

  private fun fallbackOrThrow(
      properties: AtomicAppOauthRedirectProperties,
      timeProvider: TimeProvider,
      reason: String,
  ): OauthRelayCodeStore {
    if (properties.store.failFast) {
      log.error("OAuth redirect relay store fail-fast triggered: {}", reason)
      throw IllegalStateException(reason)
    }
    log.warn(
        "{} Falling back to in-memory relay code store (process-local per instance).",
        reason,
    )
    return newInMemoryStore(
        properties = properties,
        timeProvider = timeProvider,
        selectionReason = "fallback",
    )
  }
}
