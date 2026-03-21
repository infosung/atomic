package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import tools.jackson.module.kotlin.jacksonObjectMapper

class CacheOauthRelayCodeStoreTest {
  @Test
  fun `pop should consume relay payload through atomic native remove path instead of get and evict`() {
    val cache = GuardedAtomicCache("atomicOauthRelayCode")
    val store =
        CacheOauthRelayCodeStore(
            cacheManager = SingleCacheManager(cache),
            cacheName = "atomicOauthRelayCode",
            keyPrefix = "atomic:oauth:relay:",
            ttlSeconds = 300,
            objectMapper = jacksonObjectMapper(),
            timeProvider =
                TimeProvider(Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC)),
        )

    store.save(
        relayCode = "relay-1",
        payload =
            OauthRelayPayload(
                provider = OauthProviderName.GOOGLE,
                accessToken = "access-token",
            ),
        expiresAt = Instant.parse("2026-03-14T00:05:00Z"),
    )

    val popped = store.pop("relay-1", Instant.parse("2026-03-14T00:00:01Z"))
    val poppedAgain = store.pop("relay-1", Instant.parse("2026-03-14T00:00:02Z"))

    assertNotNull(popped)
    assertEquals(OauthProviderName.GOOGLE, popped.provider)
    assertEquals("access-token", popped.accessToken)
    assertNull(poppedAgain)
    assertTrue(cache.getCalledCount == 0)
    assertTrue(cache.evictCalledCount == 0)
    assertEquals(0, cache.size())
  }

  @Test
  fun `supportsAtomicConsume should reject unsupported cache backend`() {
    val supported = GuardedAtomicCache("atomicOauthRelayCode")
    val unsupported = UnsupportedAtomicCache("atomicOauthRelayCode")

    assertTrue(CacheOauthRelayCodeStore.supportsAtomicConsume(supported))
    assertTrue(!CacheOauthRelayCodeStore.supportsAtomicConsume(unsupported))
  }

  private class SingleCacheManager(
      private val cache: Cache,
  ) : CacheManager {
    override fun getCache(name: String): Cache? = if (cache.name == name) cache else null

    override fun getCacheNames(): MutableCollection<String> = mutableListOf(cache.name)
  }

  private class GuardedAtomicCache(
      private val cacheName: String,
  ) : Cache {
    private val store = ConcurrentHashMap<Any, Any>()
    var getCalledCount: Int = 0
    var evictCalledCount: Int = 0

    override fun getName(): String = cacheName

    override fun getNativeCache(): Any = store

    override fun get(key: Any): Cache.ValueWrapper? {
      getCalledCount += 1
      throw UnsupportedOperationException(
          "get should not be used when atomic native consume is available.")
    }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
      getCalledCount += 1
      throw UnsupportedOperationException(
          "typed get should not be used when atomic native consume is available.")
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
      val existing = store.putIfAbsent(key, value ?: NullValue)
      return existing?.let { SimpleValueWrapper(it) }
    }

    override fun evict(key: Any) {
      evictCalledCount += 1
      throw UnsupportedOperationException(
          "evict should not be used when atomic native consume is available.")
    }

    override fun evictIfPresent(key: Any): Boolean = store.remove(key) != null

    override fun clear() {
      store.clear()
    }

    override fun invalidate(): Boolean = store.isNotEmpty().also { store.clear() }

    fun size(): Int = store.size
  }

  private class UnsupportedAtomicCache(
      private val cacheName: String,
  ) : Cache {
    override fun getName(): String = cacheName

    override fun getNativeCache(): Any = Any()

    override fun get(key: Any): Cache.ValueWrapper? = null

    override fun <T : Any> get(key: Any, type: Class<T>?): T? = null

    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T = valueLoader.call()

    override fun put(key: Any, value: Any?) = Unit

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? = null

    override fun evict(key: Any) = Unit

    override fun evictIfPresent(key: Any): Boolean = false

    override fun clear() = Unit

    override fun invalidate(): Boolean = false
  }

  private object NullValue

  private class SimpleValueWrapper(
      private val value: Any,
  ) : Cache.ValueWrapper {
    override fun get(): Any = value
  }
}
