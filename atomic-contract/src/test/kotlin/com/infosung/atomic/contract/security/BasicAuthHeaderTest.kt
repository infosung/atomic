package com.infosung.atomic.contract.security

import kotlin.test.Test
import kotlin.test.assertEquals

class BasicAuthHeaderTest {
  @Test
  fun `create should build base64 credential header`() {
    assertEquals("Basic dXNlcjpwYXNz", BasicAuthHeader.create("user", "pass"))
  }
}
