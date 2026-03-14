package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.AppOauthRedirectController
import com.infosung.atomic.app.oauth.AppOauthRedirectHttpExceptionHandler
import com.infosung.atomic.app.oauth.AppOauthRedirectService
import com.infosung.atomic.app.oauth.AppOauthRelayCodeService
import com.infosung.atomic.app.oauth.AllowedRedirectUriPolicy
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
  fun appOauthRedirectPropertiesValidator(
      properties: AtomicAppOauthRedirectProperties,
      oauthServiceProviderProvider: ObjectProvider<OauthServiceProvider>,
      oauthStateManagerProvider: ObjectProvider<OauthStateManager>,
  ): Any {
    validateGlobalProperties(properties)
    validateSecurityProperties(properties)
    validateRequiredOauthBeans(
        oauthServiceProviderProvider = oauthServiceProviderProvider,
        oauthStateManagerProvider = oauthStateManagerProvider,
    )
    log.debug(
        "Validated oauth redirect auto-configuration prerequisites: storeType={}, callbackBindingEnabled={}",
        properties.store.type,
        properties.callbackBinding.enabled,
    )
    return Any()
  }

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
      properties: AtomicAppOauthRedirectProperties,
  ): AppOauthRedirectController {
    return AppOauthRedirectController(
        appOauthRedirectService = appOauthRedirectService,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun appOauthRedirectHttpExceptionHandler(): AppOauthRedirectHttpExceptionHandler {
    return AppOauthRedirectHttpExceptionHandler()
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
    log.info(
        "Using in-memory oauth relay store: cleanupInterval={}, failFast={}",
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
    log.warn("{} Falling back to in-memory relay code store.", reason)
    return newInMemoryStore(properties = properties, timeProvider = timeProvider)
  }

  private fun validateSecurityProperties(properties: AtomicAppOauthRedirectProperties) {
    AllowedRedirectUriPolicy.validateConfiguredPrefixes(properties.allowedRedirectUriPrefixes)

    if (!properties.callbackBinding.enabled) {
      return
    }
    require(properties.callbackBinding.stateAttributeKey.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.state-attribute-key must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieName.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-name must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieName.startsWith("__Host-")) {
      "atomic.app.oauth.redirect.callback-binding.cookie-name must start with '__Host-' when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieSameSite.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-same-site must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookiePath.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-path must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookiePath.trim() == "/") {
      "atomic.app.oauth.redirect.callback-binding.cookie-path must be '/' when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieSecure) {
      "atomic.app.oauth.redirect.callback-binding.cookie-secure must be true when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieMaxAgeSeconds > 0) {
      "atomic.app.oauth.redirect.callback-binding.cookie-max-age-seconds must be greater than zero when callback binding is enabled."
    }
  }

  private fun validateGlobalProperties(properties: AtomicAppOauthRedirectProperties) {
    require(properties.relayCodeTtlSeconds > 0) {
      "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero."
    }
  }

  private fun validateRequiredOauthBeans(
      oauthServiceProviderProvider: ObjectProvider<OauthServiceProvider>,
      oauthStateManagerProvider: ObjectProvider<OauthStateManager>,
  ) {
    val oauthServiceProvider = oauthServiceProviderProvider.getIfAvailable()
    val oauthStateManager = oauthStateManagerProvider.getIfAvailable()
    if (
        oauthServiceProvider != null &&
            oauthStateManager != null &&
            oauthStateManager.isReplayProtectionEnabled()
    ) {
      return
    }
    val message =
        "atomic.app.oauth.redirect.enabled=true requires OauthServiceProvider and store-backed OauthStateManager beans."
    log.error(
        "{} providerPresent={}, stateManagerPresent={}, replayProtectionEnabled={}",
        message,
        oauthServiceProvider != null,
        oauthStateManager != null,
        oauthStateManager?.isReplayProtectionEnabled() == true,
    )
    throw IllegalStateException(message)
  }
}
