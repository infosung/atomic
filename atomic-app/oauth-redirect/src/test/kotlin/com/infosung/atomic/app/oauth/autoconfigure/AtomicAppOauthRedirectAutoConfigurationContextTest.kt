package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectController
import com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
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
import java.time.Instant
import java.util.concurrent.Callable
import kotlin.test.Test
import kotlin.test.assertEquals
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
  fun `disabled oauth redirect should not register relay or web beans`() {
    contextRunner.run { context ->
      assertFalse(context.containsBean("appOauthRedirectPropertiesValidator"))
      assertFalse(context.containsBean("oauthRelayCodeStore"))
      assertTrue(context.getBeansOfType(OauthRelayCodeStore::class.java).isEmpty())
      assertTrue(context.getBeansOfType(IssueOauthRelayCodeUseCase::class.java).isEmpty())
      assertTrue(context.getBeansOfType(ConsumeOauthRelayCodeUseCase::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppOauthRedirectController::class.java).isEmpty())
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
  fun `enabled in memory mode with oauth beans should register supported seams and web adapters`() {
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
          assertNotNull(context.getBean(OauthRelayCodeStore::class.java))
          assertNotNull(context.getBean(IssueOauthRelayCodeUseCase::class.java))
          assertNotNull(context.getBean(ConsumeOauthRelayCodeUseCase::class.java))
          assertNotNull(context.getBean(AppOauthRedirectController::class.java))
        }
  }

  @Test
  fun `enabled oauth redirect should accept explicit replay protection capability without direct state-store bean`() {
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
          assertNotNull(context.getBean(OauthRelayCodeStore::class.java))
          assertNotNull(context.getBean(AppOauthRedirectController::class.java))
        }
  }

  @Test
  fun `custom relay store bean should suppress default store and back relay use cases`() {
    val trackingStore = TrackingOauthRelayCodeStore()

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
        .withBean(OauthRelayCodeStore::class.java, { trackingStore })
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertEquals(1, context.getBeansOfType(OauthRelayCodeStore::class.java).size)
          assertEquals(1, context.getBeansOfType(IssueOauthRelayCodeUseCase::class.java).size)
          assertEquals(1, context.getBeansOfType(ConsumeOauthRelayCodeUseCase::class.java).size)

          val relayCode =
              context
                  .getBean(IssueOauthRelayCodeUseCase::class.java)
                  .issue(OauthRelayPayload(provider = OauthProviderName.GOOGLE, accessToken = "t1"))

          assertTrue(relayCode.isNotBlank())
          assertEquals(1, trackingStore.savedRelayCodes.size)
          assertEquals(relayCode, trackingStore.savedRelayCodes.single())
        }
  }

  @Test
  fun `custom relay issue use case bean should suppress default issue use case and back issue port`() {
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
        .withBean(
            IssueOauthRelayCodeUseCase::class.java,
            { IssueOauthRelayCodeUseCase { "custom-relay-code" } },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertEquals(1, context.getBeansOfType(IssueOauthRelayCodeUseCase::class.java).size)

          val relayCode =
              context
                  .getBean(IssueOauthRelayCodePort::class.java)
                  .issueRelayCode(OauthRelayPayload(provider = OauthProviderName.GOOGLE))

          assertEquals("custom-relay-code", relayCode)
        }
  }

  @Test
  fun `custom oauth redirect controller bean should suppress default controller bean`() {
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
        .withBean(
            AppOauthRedirectController::class.java,
            {
              AppOauthRedirectController(
                  buildAuthorizationRedirectUseCase =
                      mock(BuildAuthorizationRedirectUseCase::class.java),
                  buildOauthCallbackRedirectUseCase =
                      mock(BuildOauthCallbackRedirectUseCase::class.java),
                  buildAppleCallbackRedirectUseCase =
                      mock(BuildAppleCallbackRedirectUseCase::class.java),
                  properties =
                      AtomicAppOauthRedirectProperties().apply {
                        allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
                      },
              )
            },
        )
        .run { context ->
          assertTrue(context.startupFailure == null)
          assertEquals(1, context.getBeansOfType(AppOauthRedirectController::class.java).size)
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

  private object NullValue

  private class SimpleValueWrapper(
      private val value: Any,
  ) : Cache.ValueWrapper {
    override fun get(): Any = value
  }

  private class TrackingOauthRelayCodeStore : OauthRelayCodeStore {
    val savedRelayCodes = mutableListOf<String>()

    override fun save(relayCode: String, payload: OauthRelayPayload, expiresAt: Instant) {
      savedRelayCodes += relayCode
    }

    override fun pop(relayCode: String, now: Instant): OauthRelayPayload? = null
  }
}
