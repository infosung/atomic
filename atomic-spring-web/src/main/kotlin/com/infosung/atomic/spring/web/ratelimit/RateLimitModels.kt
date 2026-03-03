package com.infosung.atomic.spring.web.ratelimit

/** Request rate-limit policy. */
data class RateLimitPolicy(
    val limit: Long,
    val windowSeconds: Long,
) {
  init {
    require(limit > 0) { "limit must be greater than zero." }
    require(windowSeconds > 0) { "windowSeconds must be greater than zero." }
  }
}

/** Result of one rate-limit consume attempt. */
data class RateLimitDecision(
    val allowed: Boolean,
    val limit: Long,
    val remaining: Long,
    val retryAfterSeconds: Long?,
    val resetAfterSeconds: Long,
)

/** Policy for requests where key resolver cannot extract an actor key. */
enum class RateLimitMissingKeyPolicy {
  /** Reject request with `400 Bad Request`. */
  REJECT,

  /** Skip rate-limit evaluation and pass request. */
  SKIP,
}

/** Strategy for path segment used inside rate-limit storage key. */
enum class RateLimitPathKeyStrategy {
  /** Use matched rule path-prefix (or `default`) to avoid per-path-variable sharding. */
  RULE_PREFIX,

  /** Use raw request URI (legacy behavior). */
  REQUEST_URI,
}

/** Path/method based rule for [PathPrefixRateLimitPolicyResolver]. */
data class RateLimitRule(
    val pathPrefix: String?,
    val methods: Set<String>,
    val policy: RateLimitPolicy,
)
