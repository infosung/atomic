package com.infosung.atomic.spring.web.ratelimit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryRateLimitStoreTest {
  @Test
  fun `consume should allow until limit and reject over limit within same window`() {
    val store = InMemoryRateLimitStore(cleanupInterval = 10)
    val policy = RateLimitPolicy(limit = 2, windowSeconds = 60)
    val now = 1_700_000_000_000L

    val first = store.consume(key = "u1|GET|/v1/resource", policy = policy, nowMillis = now)
    val second =
        store.consume(key = "u1|GET|/v1/resource", policy = policy, nowMillis = now + 1_000)
    val third = store.consume(key = "u1|GET|/v1/resource", policy = policy, nowMillis = now + 2_000)

    assertTrue(first.allowed)
    assertEquals(1, first.remaining)
    assertTrue(second.allowed)
    assertEquals(0, second.remaining)
    assertFalse(third.allowed)
    assertEquals(0, third.remaining)
  }

  @Test
  fun `consume should reset count in next window`() {
    val store = InMemoryRateLimitStore(cleanupInterval = 10)
    val policy = RateLimitPolicy(limit = 1, windowSeconds = 10)
    val now = 1_700_000_000_000L

    val first = store.consume(key = "u1|POST|/v1/order", policy = policy, nowMillis = now)
    val blocked =
        store.consume(
            key = "u1|POST|/v1/order",
            policy = policy,
            nowMillis = now + 1_000,
        )
    val nextWindow =
        store.consume(
            key = "u1|POST|/v1/order",
            policy = policy,
            nowMillis = now + 11_000,
        )

    assertTrue(first.allowed)
    assertFalse(blocked.allowed)
    assertTrue(nextWindow.allowed)
  }

  @Test
  fun `consume should return retry-after 1 at window boundary and allow next window`() {
    val store = InMemoryRateLimitStore(cleanupInterval = 10)
    val policy = RateLimitPolicy(limit = 1, windowSeconds = 10)
    val key = "u1|GET|default"

    val first = store.consume(key = key, policy = policy, nowMillis = 9_000)
    val blocked = store.consume(key = key, policy = policy, nowMillis = 9_999)
    val nextWindow = store.consume(key = key, policy = policy, nowMillis = 10_000)

    assertTrue(first.allowed)
    assertFalse(blocked.allowed)
    assertEquals(1, blocked.retryAfterSeconds)
    assertEquals(1, blocked.resetAfterSeconds)
    assertTrue(nextWindow.allowed)
    assertEquals(10, nextWindow.resetAfterSeconds)
  }
}
