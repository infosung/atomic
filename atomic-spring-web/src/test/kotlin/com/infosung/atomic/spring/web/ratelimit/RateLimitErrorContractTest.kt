package com.infosung.atomic.spring.web.ratelimit

import kotlin.test.Test
import kotlin.test.assertEquals

class RateLimitErrorContractTest {
  @Test
  fun `rate limit error catalog should remain stable`() {
    assertEquals(
        listOf(
            Triple("RATE_LIMIT_KEY_REQUIRED", 400, "Rate-limit key is missing."),
            Triple("RATE_LIMIT_EXCEEDED", 429, "Too many requests."),
        ),
        RateLimitErrorCode.entries.map { Triple(it.name, it.defaultHttpStatus, it.defaultMessage) },
    )
  }
}
