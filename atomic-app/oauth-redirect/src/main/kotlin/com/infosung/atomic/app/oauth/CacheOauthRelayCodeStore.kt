package com.infosung.atomic.app.oauth

import com.infosung.atomic.contract.time.TimeProvider
import java.time.Instant
import org.slf4j.LoggerFactory
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
) : OauthRelayCodeStore {
  private val log = LoggerFactory.getLogger(this::class.java)

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
    val cache = cacheManager.getCache(cacheName) ?: throw cacheMissing()
    val key = cacheKey(relayCode)
    val payloadJson = cache.get(key, String::class.java) ?: return null
    cache.evict(key)

    val entry = objectMapper.readValue(payloadJson, StoredRelayValue::class.java)
    if (!entry.expiresAt.isAfter(now)) {
      return null
    }
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

  private data class StoredRelayValue(
      val payload: OauthRelayPayload,
      val expiresAt: Instant,
      val writtenAt: Instant,
  )
}
