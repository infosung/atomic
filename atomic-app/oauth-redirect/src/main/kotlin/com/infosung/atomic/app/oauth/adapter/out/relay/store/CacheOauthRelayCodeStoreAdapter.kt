package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import java.time.Instant
import java.util.concurrent.ConcurrentMap
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import tools.jackson.databind.ObjectMapper

/**
 * Adapter-backed cache relay store implementation (Redis/Caffeine via Spring Cache).
 *
 * Expiration is checked on consume (`pop`). For production, configure cache backend TTL/eviction so
 * expired and never-consumed relay entries do not accumulate.
 */
open class CacheOauthRelayCodeStoreAdapter(
    private val cacheManager: CacheManager,
    private val cacheName: String,
    private val keyPrefix: String,
    private val ttlSeconds: Long,
    private val objectMapper: ObjectMapper,
    private val timeProvider: TimeProvider = TimeProvider(),
) : OauthRelayCodeStore {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val atomicConsumeAccessor: AtomicConsumeAccessor by lazy {
    resolveRequiredAtomicConsumeAccessor()
  }

  override fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  ) {
    val cache = cacheManager.getCache(cacheName) ?: throw cacheMissing()
    val effectiveExpiresAt =
        if (ttlSeconds > 0) timeProvider.nowInstant().plusSeconds(ttlSeconds) else expiresAt
    val entry =
        StoredRelayValue(
            payload = payload,
            expiresAt = effectiveExpiresAt,
            writtenAt = timeProvider.nowInstant(),
        )
    cache.put(cacheKey(relayCode), objectMapper.writeValueAsString(entry))
    log.trace(
        "Stored oauth relayCode in cache: cacheName={}, relayCodeLength={}, ttlSeconds={}",
        cacheName,
        relayCode.length,
        ttlSeconds,
    )
  }

  override fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload? {
    val key = cacheKey(relayCode)
    val payloadJson = unwrapStoredValue(atomicConsumeAccessor.removeAndReturn(key)) ?: return null

    val entry = objectMapper.readValue(payloadJson, StoredRelayValue::class.java)
    if (!entry.expiresAt.isAfter(now)) {
      log.debug(
          "Discarded expired oauth relayCode after atomic cache consume: cacheName={}, relayCodeLength={}, strategy={}, expiresAt={}, now={}",
          cacheName,
          relayCode.length,
          atomicConsumeAccessor.description,
          entry.expiresAt,
          now,
      )
      return null
    }
    log.trace(
        "Consumed oauth relayCode from cache atomically: cacheName={}, relayCodeLength={}, strategy={}",
        cacheName,
        relayCode.length,
        atomicConsumeAccessor.description,
    )
    return entry.payload
  }

  private fun cacheKey(relayCode: String): String {
    val sanitizedPrefix = keyPrefix.trim()
    return if (sanitizedPrefix.isBlank()) relayCode else "$sanitizedPrefix$relayCode"
  }

  private fun cacheMissing(): IllegalStateException {
    return IllegalStateException(
        "Cache '$cacheName' is not available. Configure CacheManager/cache names or use another relay store type.",
    )
  }

  private fun resolveRequiredAtomicConsumeAccessor(): AtomicConsumeAccessor {
    val cache = cacheManager.getCache(cacheName) ?: throw cacheMissing()
    return resolveAtomicConsumeAccessor(cache)
        ?: throw unsupportedAtomicConsume(cacheName).also {
          log.error(
              "OAuth relay cache does not expose atomic consume support: cacheName={}, nativeCacheType={}",
              cacheName,
              cache.getNativeCache()::class.java.name,
          )
        }
  }

  private fun unwrapStoredValue(storedValue: Any?): String? {
    return when (storedValue) {
      null -> null
      is String -> storedValue
      is CharSequence -> storedValue.toString()
      is Cache.ValueWrapper -> unwrapStoredValue(storedValue.get())
      else -> {
        log.error(
            "OAuth relay cache returned unsupported stored value type during atomic consume: cacheName={}, valueType={}",
            cacheName,
            storedValue::class.java.name,
        )
        throw IllegalStateException(
            "Cache '$cacheName' returned unsupported stored value type '${storedValue::class.java.name}' during atomic relay consume.",
        )
      }
    }
  }

  private data class AtomicConsumeAccessor(
      val description: String,
      val removeAndReturn: (Any) -> Any?,
  )

  private data class StoredRelayValue(
      val payload: OauthRelayPayload,
      val expiresAt: Instant,
      val writtenAt: Instant,
  )

  companion object {
    private val staticLog = LoggerFactory.getLogger(CacheOauthRelayCodeStoreAdapter::class.java)

    fun supportsAtomicConsume(cache: Cache): Boolean = resolveAtomicConsumeAccessor(cache) != null

    fun unsupportedAtomicConsume(cacheName: String): IllegalStateException {
      return IllegalStateException(
          "Configured cache '$cacheName' does not support atomic relay consume. Provide a cache backend with remove-and-return semantics or set atomic.app.oauth.redirect.store.fail-fast=false to fall back to the in-memory relay store.",
      )
    }

    private fun resolveAtomicConsumeAccessor(cache: Cache): AtomicConsumeAccessor? {
      val nativeCache = cache.getNativeCache()
      if (nativeCache is ConcurrentMap<*, *>) {
        @Suppress("UNCHECKED_CAST") val concurrentMap = nativeCache as ConcurrentMap<Any, Any?>
        staticLog.debug(
            "Resolved oauth relay cache atomic consume path via native ConcurrentMap.remove: cacheName={}, nativeCacheType={}",
            cache.name,
            nativeCache::class.java.name,
        )
        return AtomicConsumeAccessor(
            description = "native ConcurrentMap.remove",
            removeAndReturn = { key -> concurrentMap.remove(key) },
        )
      }

      val getAndRemoveMethod =
          nativeCache.javaClass.methods.firstOrNull { method ->
            method.name == "getAndRemove" &&
                method.parameterCount == 1 &&
                method.returnType != Void.TYPE &&
                method.returnType != Boolean::class.javaObjectType &&
                method.returnType != Boolean::class.javaPrimitiveType
          }
      if (getAndRemoveMethod != null) {
        staticLog.debug(
            "Resolved oauth relay cache atomic consume path via native getAndRemove: cacheName={}, nativeCacheType={}",
            cache.name,
            nativeCache::class.java.name,
        )
        return AtomicConsumeAccessor(
            description = "native getAndRemove",
            removeAndReturn = { key -> getAndRemoveMethod.invoke(nativeCache, key) },
        )
      }

      val asMapMethod =
          nativeCache.javaClass.methods.firstOrNull { method ->
            method.name == "asMap" && method.parameterCount == 0
          }
      if (asMapMethod != null) {
        val mapView = asMapMethod.invoke(nativeCache)
        if (mapView is ConcurrentMap<*, *>) {
          @Suppress("UNCHECKED_CAST") val concurrentMap = mapView as ConcurrentMap<Any, Any?>
          staticLog.debug(
              "Resolved oauth relay cache atomic consume path via native asMap.remove: cacheName={}, nativeCacheType={}, mapViewType={}",
              cache.name,
              nativeCache::class.java.name,
              mapView::class.java.name,
          )
          return AtomicConsumeAccessor(
              description = "native asMap.remove",
              removeAndReturn = { key -> concurrentMap.remove(key) },
          )
        }
      }

      staticLog.warn(
          "No atomic oauth relay cache consume path found: cacheName={}, nativeCacheType={}",
          cache.name,
          nativeCache::class.java.name,
      )
      return null
    }
  }
}
