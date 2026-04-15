package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.random.SecureRandomSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SecureRandomSourceTest {
  @Test
  fun `nextBytes should return requested length`() {
    val source = SecureRandomSource()

    assertEquals(32, source.nextBytes(32).size)
  }

  @Test
  fun `nextString should return requested length`() {
    val source = SecureRandomSource()

    assertEquals(16, source.nextString(16).length)
  }
}
