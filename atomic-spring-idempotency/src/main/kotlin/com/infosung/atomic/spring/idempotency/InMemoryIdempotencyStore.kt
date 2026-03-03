package com.infosung.atomic.spring.idempotency

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Process-local idempotency store. Prefer shared store for multi-instance production. */
class InMemoryIdempotencyStore(
    private val cleanupInterval: Int = 1_000,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : IdempotencyStore {
  private val entries = ConcurrentHashMap<String, Entry>()
  private val operationCount = AtomicLong(0)

  override fun claim(
      key: String,
      fingerprint: String,
      expiresAtMillis: Long,
  ): IdempotencyClaimResult {
    val now = nowProvider()
    val result =
        synchronized(this) {
          val current = entries[key]
          val active =
              if (current == null || current.expiresAtMillis <= now) {
                null
              } else {
                current
              }

          if (active == null) {
            val claimToken = UUID.randomUUID().toString()
            entries[key] =
                Entry(
                    claimToken = claimToken,
                    fingerprint = fingerprint,
                    expiresAtMillis = expiresAtMillis,
                    status = Status.PROCESSING,
                )
            IdempotencyClaimResult.Claimed(claimToken = claimToken)
          } else if (active.fingerprint != fingerprint) {
            IdempotencyClaimResult.FingerprintMismatch
          } else {
            when (active.status) {
              Status.PROCESSING -> IdempotencyClaimResult.Processing
              Status.COMPLETED -> {
                val response = active.response
                if (response == null) {
                  IdempotencyClaimResult.Processing
                } else {
                  IdempotencyClaimResult.Completed(response)
                }
              }
            }
          }
        }
    cleanupExpired(now)
    return result
  }

  override fun complete(
      key: String,
      claimToken: String,
      fingerprint: String,
      response: IdempotencyStoredResponse,
      expiresAtMillis: Long,
  ) {
    synchronized(this) {
      val current = entries[key]
      if (current == null ||
          current.claimToken != claimToken ||
          current.fingerprint != fingerprint) {
        return
      }
      entries[key] =
          current.copy(
              status = Status.COMPLETED,
              response = response,
              expiresAtMillis = expiresAtMillis,
          )
    }
  }

  override fun remove(
      key: String,
      claimToken: String,
  ) {
    synchronized(this) {
      val current = entries[key] ?: return
      if (current.claimToken != claimToken) {
        return
      }
      entries.remove(key)
    }
  }

  private fun cleanupExpired(nowMillis: Long) {
    if (cleanupInterval <= 0) {
      return
    }
    val op = operationCount.incrementAndGet()
    if (op % cleanupInterval.toLong() != 0L) {
      return
    }
    entries.entries.removeIf { (_, value) -> value.expiresAtMillis <= nowMillis }
  }

  private data class Entry(
      val claimToken: String,
      val fingerprint: String,
      val expiresAtMillis: Long,
      val status: Status,
      val response: IdempotencyStoredResponse? = null,
  )

  private enum class Status {
    PROCESSING,
    COMPLETED,
  }
}
