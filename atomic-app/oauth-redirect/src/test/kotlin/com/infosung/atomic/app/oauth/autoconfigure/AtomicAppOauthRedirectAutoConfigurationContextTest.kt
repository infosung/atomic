package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.AppOauthRedirectController
import com.infosung.atomic.app.oauth.AppOauthRedirectHttpExceptionHandler
import com.infosung.atomic.app.oauth.AppOauthRedirectService
import com.infosung.atomic.app.oauth.AppOauthRelayCodeService
import com.infosung.atomic.app.oauth.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.OauthRelayCodeStore
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import java.util.concurrent.Callable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import tools.jackson.module.kotlin.jacksonObjectMapper

class AtomicAppOauthRedirectAutoConfigurationContextTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AtomicAppOauthRedirectAutoConfiguration::class.java))

  @Test
  fun `disabled oauth redirect should not register relay or controller beans`() {
    contextRunner.run { context ->
      assertFalse(context.containsBean("appOauthRedirectPropertiesValidator"))
      assertFalse(context.containsBean("oauthRelayCodeStore"))
      assertTrue(context.getBeansOfType(OauthRelayCodeStore::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppOauthRelayCodeService::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppOauthRedirectService::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppOauthRedirectController::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppOauthRedirectHttpExceptionHandler::class.java).isEmpty())
    }
  }

  @Test
  fun `enabled oauth redirect should fail startup when oauth beans are missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.enabled=true requires OauthServiceProvider and store-backed OauthStateManager beans.",
              ) == true,
          )
        }
  }

  @Test
  fun `enabled oauth redirect should fail startup when oauth state manager is missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.enabled=true requires OauthServiceProvider and store-backed OauthStateManager beans.",
              ) == true,
          )
        }
  }

  @Test
  fun `enabled in memory mode with oauth beans should register controller and service`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          assertNotNull(context.getBean(AppOauthRedirectService::class.java))
          assertNotNull(context.getBean(AppOauthRedirectController::class.java))
          assertNotNull(context.getBean(AppOauthRedirectHttpExceptionHandler::class.java))
        }
  }

  @Test
  fun `enabled oauth redirect should accept explicit replay protection capability without reflection coupling`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              mock(OauthStateManager::class.java).also {
                doReturn(true).`when`(it).isReplayProtectionEnabled()
              }
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertNotNull(context.getBean(AppOauthRedirectService::class.java))
          assertNotNull(context.getBean(AppOauthRedirectController::class.java))
          assertNotNull(context.getBean(AppOauthRedirectHttpExceptionHandler::class.java))
        }
  }

  @Test
  fun `enabled oauth redirect should fail startup when oauth state manager has no replay protection`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            { OauthStateManager("0123456789abcdef0123456789abcdef") },
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.enabled=true requires OauthServiceProvider and store-backed OauthStateManager beans.",
              ) == true,
          )
        }
  }

  @Test
  fun `disabled callback binding should make cookie settings optional`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
            "atomic.app.oauth.redirect.callback-binding.enabled=false",
            "atomic.app.oauth.redirect.callback-binding.state-attribute-key= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-name= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-same-site= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-path=/oauth/callback",
            "atomic.app.oauth.redirect.callback-binding.cookie-secure=false",
            "atomic.app.oauth.redirect.callback-binding.cookie-max-age-seconds=0",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertNotNull(context.getBean("appOauthRedirectPropertiesValidator"))
        }
  }

  @Test
  fun `disabled callback binding mode should make cookie settings optional`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
            "atomic.app.oauth.redirect.callback-binding.mode=disabled",
            "atomic.app.oauth.redirect.callback-binding.state-attribute-key= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-name= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-same-site= ",
            "atomic.app.oauth.redirect.callback-binding.cookie-path=/oauth/callback",
            "atomic.app.oauth.redirect.callback-binding.cookie-secure=false",
            "atomic.app.oauth.redirect.callback-binding.cookie-max-age-seconds=0",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertNotNull(context.getBean("appOauthRedirectPropertiesValidator"))
        }
  }

  @Test
  fun `default entity store should fail startup when required dependencies are missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.store.type=entity requires DataSource, PlatformTransactionManager, and ObjectMapper beans.",
              ) == true,
          )
        }
  }

  @Test
  fun `enabled oauth redirect should fail startup when allowlist is missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes must not be empty when redirect API is enabled.",
              ) == true,
          )
        }
  }

  @Test
  fun `enabled oauth redirect should fail startup when allowlist entry is malformed`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth?bad=1",
            "atomic.app.oauth.redirect.store.type=in-memory",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "Allowed redirect URI must not include query or fragment.",
              ) == true,
          )
        }
  }

  @Test
  fun `enabled callback binding should fail startup for invalid cookie path`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
            "atomic.app.oauth.redirect.callback-binding.enabled=true",
            "atomic.app.oauth.redirect.callback-binding.cookie-path=/oauth/callback",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.callback-binding.cookie-path must be '/' when callback binding is enabled.",
              ) == true,
          )
        }
  }

  @Test
  fun `relaxed callback binding mode should still fail startup for invalid cookie path`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=in-memory",
            "atomic.app.oauth.redirect.callback-binding.mode=relaxed",
            "atomic.app.oauth.redirect.callback-binding.cookie-path=/oauth/callback",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.callback-binding.cookie-path must be '/' when callback binding is enabled.",
              ) == true,
          )
        }
  }

  @Test
  fun `cache store with fail fast disabled should fallback to in memory store`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.fail-fast=false",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertIs<InMemoryOauthRelayCodeStore>(context.getBean(OauthRelayCodeStore::class.java))
        }
  }

  @Test
  fun `cache store with atomic cache backend should register cache relay store`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.cache.cache-name=atomicOauthRelayCode",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .withBean(
            CacheManager::class.java,
            { SingleCacheManager(AtomicConcurrentMapCache("atomicOauthRelayCode")) },
        )
        .withBean(tools.jackson.databind.ObjectMapper::class.java, { jacksonObjectMapper() })
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertIs<CacheOauthRelayCodeStore>(context.getBean(OauthRelayCodeStore::class.java))
        }
  }

  @Test
  fun `cache store with unsupported cache backend and fail fast disabled should fallback to in memory store`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.fail-fast=false",
            "atomic.app.oauth.redirect.store.cache.cache-name=atomicOauthRelayCode",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .withBean(
            CacheManager::class.java,
            { SingleCacheManager(UnsupportedAtomicCache("atomicOauthRelayCode")) },
        )
        .withBean(tools.jackson.databind.ObjectMapper::class.java, { jacksonObjectMapper() })
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertIs<InMemoryOauthRelayCodeStore>(context.getBean(OauthRelayCodeStore::class.java))
        }
  }

  @Test
  fun `cache store with unsupported cache backend and fail fast enabled should fail startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.fail-fast=true",
            "atomic.app.oauth.redirect.store.cache.cache-name=atomicOauthRelayCode",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .withBean(
            CacheManager::class.java,
            { SingleCacheManager(UnsupportedAtomicCache("atomicOauthRelayCode")) },
        )
        .withBean(tools.jackson.databind.ObjectMapper::class.java, { jacksonObjectMapper() })
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "Configured cache 'atomicOauthRelayCode' does not support atomic relay consume.",
              ) == true,
          )
        }
  }

  @Test
  fun `cache store with fail fast enabled should fail startup when dependencies are missing`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.fail-fast=true",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains(
                  "atomic.app.oauth.redirect.store.type=cache requires CacheManager and ObjectMapper beans.",
              ) == true,
          )
        }
  }

  @Test
  fun `entity store with fail fast disabled should fallback to in memory store`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://app.example.com/oauth",
            "atomic.app.oauth.redirect.store.type=entity",
            "atomic.app.oauth.redirect.store.fail-fast=false",
        )
        .withBean(
            OauthServiceProvider::class.java,
            { OauthServiceProvider(listOf(TestOauthProvider())) },
        )
        .withBean(
            OauthStateManager::class.java,
            {
              OauthStateManager(
                  signingSecret = "0123456789abcdef0123456789abcdef",
                  store = InMemoryOauthStateStore(),
              )
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertIs<InMemoryOauthRelayCodeStore>(context.getBean(OauthRelayCodeStore::class.java))
        }
  }

  private class TestOauthProvider : OauthProvider {
    override val providerName: OauthProviderName = OauthProviderName.GOOGLE

    override fun capabilities(): Set<OauthProviderCapability> =
        setOf(
            OauthProviderCapability.AUTHORIZATION_URL,
            OauthProviderCapability.EXCHANGE_TOKEN,
        )

    override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String =
        "https://provider.example.com/auth"

    override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult =
        OauthTokenResult(accessToken = "access-token")

    override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult =
        OauthTokenResult()

    override fun revokeToken(request: OauthTokenRevokeRequest) = Unit

    override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
      return OauthIdentityResult(provider = providerName, userId = "user-1")
    }
  }

  private class SingleCacheManager(
      private val cache: Cache,
  ) : CacheManager {
    override fun getCache(name: String): Cache? = if (cache.name == name) cache else null

    override fun getCacheNames(): MutableCollection<String> = mutableListOf(cache.name)
  }

  private class AtomicConcurrentMapCache(
      private val cacheName: String,
  ) : Cache {
    private val store = java.util.concurrent.ConcurrentHashMap<Any, Any>()

    override fun getName(): String = cacheName

    override fun getNativeCache(): Any = store

    override fun get(key: Any): Cache.ValueWrapper? {
      throw UnsupportedOperationException("get should not be used for atomic relay consume.")
    }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
      throw UnsupportedOperationException("typed get should not be used for atomic relay consume.")
    }

    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T {
      throw UnsupportedOperationException("loading get is not supported in this test cache.")
    }

    override fun put(key: Any, value: Any?) {
      if (value != null) {
        store[key] = value
      } else {
        store.remove(key)
      }
    }

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? {
      val existing =
          if (value != null) store.putIfAbsent(key, value) else store.putIfAbsent(key, NullValue)
      return existing?.let { SimpleValueWrapper(it) }
    }

    override fun evict(key: Any) {
      throw UnsupportedOperationException("evict should not be used for atomic relay consume.")
    }

    override fun evictIfPresent(key: Any): Boolean = store.remove(key) != null

    override fun clear() {
      store.clear()
    }

    override fun invalidate(): Boolean = store.isNotEmpty().also { store.clear() }
  }

  private class UnsupportedAtomicCache(
      private val cacheName: String,
  ) : Cache {
    private val store = mutableMapOf<Any, Any>()

    override fun getName(): String = cacheName

    override fun getNativeCache(): Any = Any()

    override fun get(key: Any): Cache.ValueWrapper? = store[key]?.let { SimpleValueWrapper(it) }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? = type?.cast(store[key])

    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T {
      val existing = store[key]
      if (existing != null) {
        @Suppress("UNCHECKED_CAST")
        return existing as T
      }
      val loaded = valueLoader.call()
      if (loaded != null) {
        store[key] = loaded as Any
      }
      return loaded
    }

    override fun put(key: Any, value: Any?) {
      if (value != null) {
        store[key] = value
      } else {
        store.remove(key)
      }
    }

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? {
      val existing = store.putIfAbsent(key, value ?: NullValue)
      return existing?.let { SimpleValueWrapper(it) }
    }

    override fun evict(key: Any) {
      store.remove(key)
    }

    override fun evictIfPresent(key: Any): Boolean = store.remove(key) != null

    override fun clear() {
      store.clear()
    }

    override fun invalidate(): Boolean = store.isNotEmpty().also { store.clear() }
  }

  private object NullValue

  private class SimpleValueWrapper(
      private val value: Any,
  ) : Cache.ValueWrapper {
    override fun get(): Any = value
  }
}
