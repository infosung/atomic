package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStoreAdapter
import com.infosung.atomic.contract.time.TimeProvider
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import tools.jackson.databind.ObjectMapper

/**
 * Cache-based relay store implementation (Redis/Caffeine via Spring Cache).
 *
 * Expiration is checked on consume (`pop`). For production, configure cache backend TTL/eviction so
 * expired and never-consumed relay entries do not accumulate.
 */
class CacheOauthRelayCodeStore(
    private val cacheManager: CacheManager,
    private val cacheName: String,
    private val keyPrefix: String,
    private val ttlSeconds: Long,
    private val objectMapper: ObjectMapper,
    private val timeProvider: TimeProvider = TimeProvider(),
) :
    CacheOauthRelayCodeStoreAdapter(
        cacheManager = cacheManager,
        cacheName = cacheName,
        keyPrefix = keyPrefix,
        ttlSeconds = ttlSeconds,
        objectMapper = objectMapper,
        timeProvider = timeProvider,
    ) {
  companion object {
    fun supportsAtomicConsume(cache: Cache): Boolean =
        CacheOauthRelayCodeStoreAdapter.supportsAtomicConsume(cache)

    fun unsupportedAtomicConsume(cacheName: String): IllegalStateException =
        CacheOauthRelayCodeStoreAdapter.unsupportedAtomicConsume(cacheName)
  }
}
