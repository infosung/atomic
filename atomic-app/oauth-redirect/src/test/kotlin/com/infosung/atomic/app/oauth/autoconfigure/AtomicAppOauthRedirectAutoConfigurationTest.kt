package com.infosung.atomic.app.oauth.autoconfigure

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.JpaOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeRepository
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.state.OauthStateStore
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.ObjectMapper

class AtomicAppOauthRedirectAutoConfigurationTest {
  private val autoConfiguration = AtomicAppOauthRedirectAutoConfiguration()
  private val relayAutoConfiguration = AtomicAppOauthRedirectRelayAutoConfiguration()

  @Test
  fun `validator should log deployment summary and process local warnings for in memory preset`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY
        }
    val stateStore = InMemoryOauthStateStore()

    withListAppender(AtomicAppOauthRedirectAutoConfiguration::class.java, Level.INFO) { events ->
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider =
              provider(OauthServiceProvider::class.java, OauthServiceProvider(emptyList())),
          oauthStateManagerProvider =
              provider(
                  OauthStateManager::class.java,
                  OauthStateManager(
                      signingSecret = "0123456789abcdef0123456789abcdef",
                      store = stateStore,
                  ),
              ),
          oauthStateStoreProvider = provider(OauthStateStore::class.java, stateStore),
      )

      val logs = events.map { it.formattedMessage }
      assertTrue(
          logs.any {
            it.contains("OAuth redirect deployment summary") &&
                it.contains("relayStoreType=IN_MEMORY") &&
                it.contains("callbackBindingMode=STRICT") &&
                it.contains("stateStoreType=IN_MEMORY")
          })
      assertTrue(logs.any { it.contains("process-local") && it.contains("relay store") })
      assertTrue(
          logs.any { it.contains("process-local") && it.contains("state replay protection") })
    }
  }

  @Test
  fun `validator should warn when callback binding mode is disabled`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.DISABLED
        }
    val stateStore = InMemoryOauthStateStore()

    withListAppender(AtomicAppOauthRedirectAutoConfiguration::class.java, Level.INFO) { events ->
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider =
              provider(OauthServiceProvider::class.java, OauthServiceProvider(emptyList())),
          oauthStateManagerProvider =
              provider(
                  OauthStateManager::class.java,
                  OauthStateManager(
                      signingSecret = "0123456789abcdef0123456789abcdef",
                      store = stateStore,
                  ),
              ),
          oauthStateStoreProvider = provider(OauthStateStore::class.java, stateStore),
      )

      val logs = events.map { it.formattedMessage }
      assertTrue(
          logs.any {
            it.contains("callback binding mode is disabled") &&
                it.contains("local HTTP-only testing")
          })
    }
  }

  @Test
  fun `validator should not require unique oauth state store bean for deployment summary`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY
        }
    val primaryStateStore = InMemoryOauthStateStore()
    val secondaryStateStore = InMemoryOauthStateStore()

    withListAppender(AtomicAppOauthRedirectAutoConfiguration::class.java, Level.INFO) { events ->
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider =
              provider(OauthServiceProvider::class.java, OauthServiceProvider(emptyList())),
          oauthStateManagerProvider =
              provider(
                  OauthStateManager::class.java,
                  OauthStateManager(
                      signingSecret = "0123456789abcdef0123456789abcdef",
                      store = primaryStateStore,
                  ),
              ),
          oauthStateStoreProvider =
              provider(
                  OauthStateStore::class.java,
                  primaryStateStore,
                  secondaryStateStore,
              ),
      )

      val logs = events.map { it.formattedMessage }
      assertTrue(logs.any { it.contains("stateStoreType=MULTIPLE_CANDIDATES") })
    }
  }

  @Test
  fun `default store type should be entity and fail when dependencies are missing`() {
    val properties = configuredProperties()

    assertFailsWith<IllegalStateException> {
      relayAutoConfiguration.oauthRelayCodeStore(
          properties = properties,
          timeProviderProvider = provider(),
          objectMapperProvider = provider(),
          cacheManagerProvider = provider(),
          oauthRelayCodeRepositoryProvider = provider(),
          dataSourceProvider = provider(),
          transactionManagerProvider = provider(),
      )
    }
  }

  @Test
  fun `allowlist should fail fast when redirect API is enabled`() {
    val properties = AtomicAppOauthRedirectProperties()

    assertFailsWith<IllegalArgumentException> {
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider = provider(),
          oauthStateManagerProvider = provider(),
          oauthStateStoreProvider = provider(),
      )
    }
  }

  @Test
  fun `in-memory type should not validate cache and entity dependencies`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY
        }

    val store =
        relayAutoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(),
            cacheManagerProvider = provider(),
            oauthRelayCodeRepositoryProvider = provider(),
            dataSourceProvider = provider(),
            transactionManagerProvider = provider(),
        )

    assertIs<InMemoryOauthRelayCodeStore>(store)
  }

  @Test
  fun `relay-code-ttl-seconds should fail fast when non-positive`() {
    val properties = configuredProperties().apply { relayCodeTtlSeconds = 0 }

    assertFailsWith<IllegalArgumentException> {
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider = provider(),
          oauthStateManagerProvider = provider(),
          oauthStateStoreProvider = provider(),
      )
    }
  }

  @Test
  fun `validator should fail fast even when custom relay store bean is used`() {
    val properties = AtomicAppOauthRedirectProperties()

    assertFailsWith<IllegalArgumentException> {
      autoConfiguration.appOauthRedirectPropertiesValidator(
          properties = properties,
          oauthServiceProviderProvider = provider(),
          oauthStateManagerProvider = provider(),
          oauthStateStoreProvider = provider(),
      )
    }
  }

  @Test
  fun `callback-binding required properties should fail fast when invalid`() {
    val invalidCases: List<(AtomicAppOauthRedirectProperties) -> Unit> =
        listOf(
            { it.callbackBinding.stateAttributeKey = " " },
            { it.callbackBinding.cookieName = " " },
            { it.callbackBinding.cookieName = "atomic_oauth_callback_binding" },
            { it.callbackBinding.cookieSameSite = " " },
            { it.callbackBinding.cookiePath = " " },
            { it.callbackBinding.cookiePath = "/oauth/callback" },
            { it.callbackBinding.cookieSecure = false },
            { it.callbackBinding.cookieMaxAgeSeconds = 0 },
        )

    invalidCases.forEach { mutate ->
      val properties = configuredProperties().apply { mutate(this) }

      assertFailsWith<IllegalArgumentException> {
        autoConfiguration.appOauthRedirectPropertiesValidator(
            properties = properties,
            oauthServiceProviderProvider = provider(),
            oauthStateManagerProvider = provider(),
            oauthStateStoreProvider = provider(),
        )
      }
    }
  }

  @Test
  fun `allowlist entries with invalid structure should fail fast`() {
    val invalidPrefixes =
        listOf(
            "https://app.example.com/oauth?bad=1",
            "https://user@app.example.com/oauth",
            "/oauth/callback",
        )

    invalidPrefixes.forEach { invalidPrefix ->
      val properties =
          configuredProperties().apply { allowedRedirectUriPrefixes = listOf(invalidPrefix) }

      val exception =
          assertFailsWith<IllegalArgumentException> {
            autoConfiguration.appOauthRedirectPropertiesValidator(
                properties = properties,
                oauthServiceProviderProvider = provider(),
                oauthStateManagerProvider = provider(),
                oauthStateStoreProvider = provider(),
            )
          }

      assertTrue(exception.message?.isNotBlank() == true)
    }
  }

  @Test
  fun `cache type with fail-fast disabled should fallback to in-memory when cache dependency is missing`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.CACHE
          store.failFast = false
        }

    val store =
        withListAppender(
            AtomicAppOauthRedirectRelayAutoConfiguration::class.java,
            Level.INFO,
        ) { events ->
          val resolvedStore =
              relayAutoConfiguration.oauthRelayCodeStore(
                  properties = properties,
                  timeProviderProvider = provider(),
                  objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
                  cacheManagerProvider = provider(),
                  oauthRelayCodeRepositoryProvider = provider(),
                  dataSourceProvider = provider(),
                  transactionManagerProvider = provider(),
              )

          val logs = events.map { it.formattedMessage }
          assertTrue(
              logs.any {
                it.contains("Falling back to in-memory relay code store") &&
                    it.contains("process-local")
              })
          resolvedStore
        }

    assertIs<InMemoryOauthRelayCodeStore>(store)
  }

  @Test
  fun `cache type with missing configured cache should fail when fail-fast enabled`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.CACHE
        }

    assertFailsWith<IllegalStateException> {
      relayAutoConfiguration.oauthRelayCodeStore(
          properties = properties,
          timeProviderProvider = provider(),
          objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
          cacheManagerProvider =
              provider(
                  CacheManager::class.java,
                  object : CacheManager {
                    override fun getCache(name: String): Cache? = null

                    override fun getCacheNames(): MutableCollection<String> = mutableListOf()
                  },
              ),
          oauthRelayCodeRepositoryProvider = provider(),
          dataSourceProvider = provider(),
          transactionManagerProvider = provider(),
      )
    }
  }

  @Test
  fun `entity type should create entity store when dependencies are present`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.ENTITY
        }

    val store =
        relayAutoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
            cacheManagerProvider =
                provider(
                    org.springframework.cache.CacheManager::class.java,
                    ConcurrentMapCacheManager("atomicOauthRelayCode"),
                ),
            oauthRelayCodeRepositoryProvider =
                provider(
                    OauthRelayCodeRepository::class.java,
                    mock(OauthRelayCodeRepository::class.java),
                ),
            dataSourceProvider = provider(DataSource::class.java, mock(DataSource::class.java)),
            transactionManagerProvider =
                provider(
                    PlatformTransactionManager::class.java,
                    mock(PlatformTransactionManager::class.java),
                ),
        )

    assertIs<JpaOauthRelayCodeStore>(store)
  }

  @Test
  fun `entity type should fallback to legacy jdbc store when custom table name is configured`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.ENTITY
          store.entity.tableName = "custom_oauth_relay_code"
        }

    val store =
        relayAutoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
            cacheManagerProvider =
                provider(
                    org.springframework.cache.CacheManager::class.java,
                    ConcurrentMapCacheManager("atomicOauthRelayCode"),
                ),
            oauthRelayCodeRepositoryProvider =
                provider(
                    OauthRelayCodeRepository::class.java,
                    mock(OauthRelayCodeRepository::class.java),
                ),
            dataSourceProvider = provider(DataSource::class.java, mock(DataSource::class.java)),
            transactionManagerProvider =
                provider(
                    PlatformTransactionManager::class.java,
                    mock(PlatformTransactionManager::class.java),
                ),
        )

    assertIs<EntityOauthRelayCodeStore>(store)
  }

  private fun <T : Any> provider(
      type: Class<T>,
      bean: T? = null,
  ): ObjectProvider<T> {
    val beanFactory = DefaultListableBeanFactory()
    if (bean != null) {
      beanFactory.registerSingleton(type.name, bean)
    }
    return beanFactory.getBeanProvider(type)
  }

  private fun <T : Any> provider(
      type: Class<T>,
      firstBean: T,
      secondBean: T,
  ): ObjectProvider<T> {
    val beanFactory = DefaultListableBeanFactory()
    beanFactory.registerSingleton("${type.name}#1", firstBean)
    beanFactory.registerSingleton("${type.name}#2", secondBean)
    return beanFactory.getBeanProvider(type)
  }

  private inline fun <reified T : Any> provider(): ObjectProvider<T> {
    return provider(T::class.java)
  }

  private fun configuredProperties(): AtomicAppOauthRedirectProperties {
    return AtomicAppOauthRedirectProperties().apply {
      allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
    }
  }

  private fun <T> withListAppender(
      loggerClass: Class<*>,
      level: Level,
      block: (events: List<ILoggingEvent>) -> T,
  ): T {
    val logger = LoggerFactory.getLogger(loggerClass) as Logger
    val previousLevel = logger.level
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)
    logger.level = level
    try {
      return block(appender.list)
    } finally {
      logger.detachAppender(appender)
      logger.level = previousLevel
    }
  }
}
