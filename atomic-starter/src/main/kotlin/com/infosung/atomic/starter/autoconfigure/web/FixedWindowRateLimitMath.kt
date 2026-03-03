package com.infosung.atomic.starter.autoconfigure.web

internal data class FixedWindowRateLimitState(
    val windowStartSeconds: Long,
    val resetAfterSeconds: Long,
    val expireSeconds: Long,
)

internal fun calculateFixedWindowRateLimitState(
    nowMillis: Long,
    windowSeconds: Long,
): FixedWindowRateLimitState {
  require(windowSeconds > 0) { "windowSeconds must be greater than zero." }
  val nowSeconds = nowMillis / 1_000
  val windowStart = nowSeconds - (nowSeconds % windowSeconds)
  val resetAfterSeconds = (windowStart + windowSeconds - nowSeconds).coerceAtLeast(0)
  return FixedWindowRateLimitState(
      windowStartSeconds = windowStart,
      resetAfterSeconds = resetAfterSeconds,
      expireSeconds = resetAfterSeconds.coerceAtLeast(1) + 1,
  )
}
