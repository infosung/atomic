package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.AppOauthRedirectController
import com.infosung.atomic.app.oauth.AppOauthRedirectService
import com.infosung.atomic.app.oauth.AppOauthRelayCodeService
import com.infosung.atomic.app.oauth.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.OauthRelayCodeStore
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.OauthStateManager
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/** Auto-configuration for app-level OAuth redirect/callback relay API. */
@AutoConfiguration(
    afterName = ["com.infosung.atomic.starter.autoconfigure.oauth2.AtomicOauth2AutoConfiguration"])
@ConditionalOnClass(
    name =
        [
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.GetMapping",
            "com.infosung.atomic.oauth.api.OauthServiceProvider",
            "com.infosung.atomic.oauth.state.OauthStateManager",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
class AtomicAppOauthRedirectAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun oauthRelayCodeStore(
      properties: AtomicAppOauthRedirectProperties,
      timeProviderProvider: ObjectProvider<TimeProvider>,
      objectMapperProvider: ObjectProvider<ObjectMapper>,
      cacheManagerProvider: ObjectProvider<org.springframework.cache.CacheManager>,
      dataSourceProvider: ObjectProvider<DataSource>,
      transactionManagerProvider: ObjectProvider<PlatformTransactionManager>,
  ): OauthRelayCodeStore {
    require(properties.relayCodeTtlSeconds > 0) {
      "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero."
    }
    val timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() }

    return when (properties.store.type) {
      AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY -> {
        newInMemoryStore(properties = properties, timeProvider = timeProvider)
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
  fun appOauthRelayCodeService(
      oauthRelayCodeStore: OauthRelayCodeStore,
      properties: AtomicAppOauthRedirectProperties,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): AppOauthRelayCodeService {
    return AppOauthRelayCodeService(
        relayCodeStore = oauthRelayCodeStore,
        properties = properties,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(OauthServiceProvider::class, OauthStateManager::class)
  fun appOauthRedirectService(
      oauthServiceProvider: OauthServiceProvider,
      oauthStateManager: OauthStateManager,
      appOauthRelayCodeService: AppOauthRelayCodeService,
      properties: AtomicAppOauthRedirectProperties,
  ): AppOauthRedirectService {
    return AppOauthRedirectService(
        oauthServiceProvider = oauthServiceProvider,
        oauthStateManager = oauthStateManager,
        relayCodeService = appOauthRelayCodeService,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(AppOauthRedirectService::class)
  fun appOauthRedirectController(
      appOauthRedirectService: AppOauthRedirectService,
  ): AppOauthRedirectController {
    return AppOauthRedirectController(
        appOauthRedirectService = appOauthRedirectService,
    )
  }

  private fun newInMemoryStore(
      properties: AtomicAppOauthRedirectProperties,
      timeProvider: TimeProvider,
  ): OauthRelayCodeStore {
    if (properties.store.inMemory.cleanupInterval <= 0) {
      log.warn(
          "atomic.app.oauth.redirect.store.in-memory.cleanup-interval is {}. Expired entries are not cleaned up periodically.",
          properties.store.inMemory.cleanupInterval,
      )
    }
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
      throw IllegalStateException(reason)
    }
    log.warn("{} Falling back to in-memory relay code store.", reason)
    return newInMemoryStore(properties = properties, timeProvider = timeProvider)
  }
}
