package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.ObjectMapper

class AtomicAppOauthRedirectAutoConfigurationTest {
  private val autoConfiguration = AtomicAppOauthRedirectAutoConfiguration()

  @Test
  fun `default store type should be entity and fail when dependencies are missing`() {
    val properties = configuredProperties()

    assertFailsWith<IllegalStateException> {
      autoConfiguration.oauthRelayCodeStore(
          properties = properties,
          timeProviderProvider = provider(),
          objectMapperProvider = provider(),
          cacheManagerProvider = provider(),
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
        autoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(),
            cacheManagerProvider = provider(),
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
          configuredProperties().apply {
            allowedRedirectUriPrefixes = listOf(invalidPrefix)
          }

      val exception =
          assertFailsWith<IllegalArgumentException> {
            autoConfiguration.appOauthRedirectPropertiesValidator(
                properties = properties,
                oauthServiceProviderProvider = provider(),
                oauthStateManagerProvider = provider(),
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
        autoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
            cacheManagerProvider = provider(),
            dataSourceProvider = provider(),
            transactionManagerProvider = provider(),
        )

    assertIs<InMemoryOauthRelayCodeStore>(store)
  }

  @Test
  fun `cache type with missing configured cache should fail when fail-fast enabled`() {
    val properties =
        configuredProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.CACHE
        }

    assertFailsWith<IllegalStateException> {
      autoConfiguration.oauthRelayCodeStore(
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
        autoConfiguration.oauthRelayCodeStore(
            properties = properties,
            timeProviderProvider = provider(),
            objectMapperProvider = provider(ObjectMapper::class.java, ObjectMapper()),
            cacheManagerProvider =
                provider(
                    org.springframework.cache.CacheManager::class.java,
                    ConcurrentMapCacheManager("atomicOauthRelayCode"),
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

  private inline fun <reified T : Any> provider(): ObjectProvider<T> {
    return provider(T::class.java)
  }

  private fun configuredProperties(): AtomicAppOauthRedirectProperties {
    return AtomicAppOauthRedirectProperties().apply {
      allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
    }
  }
}
