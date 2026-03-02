package com.infosung.atomic.oauth.state

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Storage abstraction for one-time OAuth state tokens.
 */
interface OauthStateStore {
  /**
   * Saves state token metadata.
   */
  fun save(stateId: String, signedState: String, expiresAt: Instant)

  /**
   * Consumes one-time state token.
   *
   * @return true when token is valid and consumed exactly once.
   */
  fun consume(stateId: String, signedState: String): Boolean
}

/**
 * In-memory [OauthStateStore] for single-node deployments.
 */
class InMemoryOauthStateStore(
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val cleanupInterval: Int = 100,
) : OauthStateStore {
  private data class StoredState(
      val signedState: String,
      val expiresAt: Instant,
  )

  private val states = ConcurrentHashMap<String, StoredState>()
  private val operationCount = AtomicInteger(0)

  init {
    require(cleanupInterval > 0) { "cleanupInterval must be greater than zero." }
  }

  override fun save(stateId: String, signedState: String, expiresAt: Instant) {
    maybeCleanup()
    states[stateId] = StoredState(signedState = signedState, expiresAt = expiresAt)
  }

  override fun consume(stateId: String, signedState: String): Boolean {
    maybeCleanup()
    val found = states[stateId] ?: return false
    val now = Instant.now(clock)
    if (!found.expiresAt.isAfter(now)) {
      states.remove(stateId, found)
      return false
    }
    if (found.signedState != signedState) {
      return false
    }
    return states.remove(stateId, found)
  }

  internal fun currentSize(): Int = states.size

  private fun maybeCleanup() {
    val count = operationCount.incrementAndGet()
    if (count % cleanupInterval != 0) {
      return
    }
    val now = Instant.now(clock)
    states.entries.removeIf { (_, value) -> !value.expiresAt.isAfter(now) }
  }
}
