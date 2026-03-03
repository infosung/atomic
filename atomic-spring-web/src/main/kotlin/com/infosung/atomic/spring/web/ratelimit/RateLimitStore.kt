package com.infosung.atomic.spring.web.ratelimit

/** Consumes one request token from backend store for [key] and [policy]. */
fun interface RateLimitStore {
  fun consume(
      key: String,
      policy: RateLimitPolicy,
      nowMillis: Long,
  ): RateLimitDecision
}
