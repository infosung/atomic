package com.infosung.atomic.starter.autoconfigure.web

import kotlin.test.Test
import kotlin.test.assertEquals

class FixedWindowRateLimitMathTest {
  @Test
  fun `should calculate reset and expire near window boundary`() {
    val state = calculateFixedWindowRateLimitState(nowMillis = 59_999, windowSeconds = 60)

    assertEquals(0, state.windowStartSeconds)
    assertEquals(1, state.resetAfterSeconds)
    assertEquals(2, state.expireSeconds)
  }

  @Test
  fun `should reset exactly on boundary`() {
    val state = calculateFixedWindowRateLimitState(nowMillis = 60_000, windowSeconds = 60)

    assertEquals(60, state.windowStartSeconds)
    assertEquals(60, state.resetAfterSeconds)
    assertEquals(61, state.expireSeconds)
  }
}
