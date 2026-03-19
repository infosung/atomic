package com.infosung.atomic.app.oauth.adapter.out.relay.store

import com.infosung.atomic.app.oauth.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Adapter-backed in-memory relay store for single-instance/local environments. */
open class InMemoryOauthRelayCodeStoreAdapter(
    private val cleanupInterval: Int = 100,
    private val timeProvider: TimeProvider = TimeProvider(),
) : OauthRelayCodeStore {
  private val entries = ConcurrentHashMap<String, RelayEntry>()
  private val operationCount = AtomicInteger()

  override fun save(
      relayCode: String,
      payload: OauthRelayPayload,
      expiresAt: Instant,
  ) {
    entries[relayCode] = RelayEntry(payload = payload, expiresAt = expiresAt)
    cleanupIfNeeded(now = timeProvider.nowInstant())
  }

  override fun pop(
      relayCode: String,
      now: Instant,
  ): OauthRelayPayload? {
    val removed = entries.remove(relayCode) ?: return null
    if (!removed.expiresAt.isAfter(now)) {
      return null
    }
    cleanupIfNeeded(now = now)
    return removed.payload
  }

  private fun cleanupIfNeeded(now: Instant) {
    if (cleanupInterval <= 0) {
      return
    }
    if (operationCount.incrementAndGet() % cleanupInterval != 0) {
      return
    }
    entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
  }

  private data class RelayEntry(
      val payload: OauthRelayPayload,
      val expiresAt: Instant,
  )
}
