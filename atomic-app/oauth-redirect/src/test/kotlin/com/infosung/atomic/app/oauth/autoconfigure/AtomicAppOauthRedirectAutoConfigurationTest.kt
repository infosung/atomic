package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
    val properties = AtomicAppOauthRedirectProperties()

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
  fun `in-memory type should not validate cache and entity dependencies`() {
    val properties =
        AtomicAppOauthRedirectProperties().apply {
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
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          store.type = AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY
          relayCodeTtlSeconds = 0
        }

    assertFailsWith<IllegalArgumentException> {
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
  fun `cache type with fail-fast disabled should fallback to in-memory when cache dependency is missing`() {
    val properties =
        AtomicAppOauthRedirectProperties().apply {
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
        AtomicAppOauthRedirectProperties().apply {
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
        AtomicAppOauthRedirectProperties().apply {
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
}
