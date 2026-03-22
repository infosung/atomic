package com.infosung.atomic.spring.web.ratelimit

enum class RateLimitErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  RATE_LIMIT_KEY_REQUIRED(400, "Rate-limit key is missing."),
  RATE_LIMIT_EXCEEDED(429, "Too many requests."),
}
