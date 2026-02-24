package com.infosung.atomic.contract.random

import kotlin.test.Test
import kotlin.test.assertTrue

class RandomUtilTest {
  @Test
  fun `randomString with explicit length should match requested size and charset`() {
    val value = RandomUtil.randomString(20)

    assertTrue(value.length == 20)
    assertTrue(value.matches(Regex("^[A-Za-z0-9]+$")))
  }

  @Test
  fun `randomString without length should use default range`() {
    val value = RandomUtil.randomString()

    assertTrue(value.length in 10..19)
    assertTrue(value.matches(Regex("^[A-Za-z0-9]+$")))
  }
}
