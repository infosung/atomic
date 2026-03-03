package com.infosung.atomic.spring.web.ratelimit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** In-memory rate-limit store. Suitable for local/single-instance usage. */
class InMemoryRateLimitStore(
    private val cleanupInterval: Int = 1_000,
) : RateLimitStore {
  private val counters = ConcurrentHashMap<String, WindowCounter>()
  private val operationCount = AtomicLong(0)

  override fun consume(
      key: String,
      policy: RateLimitPolicy,
      nowMillis: Long,
  ): RateLimitDecision {
    val nowSeconds = nowMillis / 1_000
    val windowStart = nowSeconds - (nowSeconds % policy.windowSeconds)
    val compositeKey = "$key|${policy.limit}|${policy.windowSeconds}"

    val decision =
        synchronized(this) {
          val counter = counters[compositeKey]
          val activeCounter =
              if (counter == null || counter.windowStartEpochSeconds != windowStart) {
                WindowCounter(
                        windowStartEpochSeconds = windowStart,
                        count = 0,
                        windowSeconds = policy.windowSeconds,
                    )
                    .also { counters[compositeKey] = it }
              } else {
                counter
              }

          val resetAfter = (windowStart + policy.windowSeconds - nowSeconds).coerceAtLeast(0)
          if (activeCounter.count >= policy.limit) {
            RateLimitDecision(
                allowed = false,
                limit = policy.limit,
                remaining = 0,
                retryAfterSeconds = resetAfter.coerceAtLeast(1),
                resetAfterSeconds = resetAfter,
            )
          } else {
            activeCounter.count += 1
            RateLimitDecision(
                allowed = true,
                limit = policy.limit,
                remaining = (policy.limit - activeCounter.count).coerceAtLeast(0),
                retryAfterSeconds = null,
                resetAfterSeconds = resetAfter,
            )
          }
        }

    cleanupExpired(nowSeconds)
    return decision
  }

  private fun cleanupExpired(nowSeconds: Long) {
    if (cleanupInterval <= 0) {
      return
    }
    val op = operationCount.incrementAndGet()
    if (op % cleanupInterval.toLong() != 0L) {
      return
    }
    counters.entries.removeIf { (_, value) ->
      val expiry = value.windowStartEpochSeconds + value.windowSeconds
      expiry <= nowSeconds
    }
  }

  private data class WindowCounter(
      val windowStartEpochSeconds: Long,
      var count: Long,
      val windowSeconds: Long,
  )
}
