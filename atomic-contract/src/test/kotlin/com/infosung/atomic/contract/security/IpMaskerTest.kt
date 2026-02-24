package com.infosung.atomic.contract.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IpMaskerTest {
  @Test
  fun `mask should mask ipv4 and keep invalid values`() {
    assertEquals("192.168.123.0/24", IpMasker.mask("192.168.123.45"))
    assertEquals("12342", IpMasker.mask("12342"))
    assertEquals("", IpMasker.mask(""))
  }

  @Test
  fun `mask should mask ipv6 with slash 64`() {
    val masked = IpMasker.mask("2001:db8:85a3:0:0:8a2e:370:7334")

    assertTrue(masked.endsWith("/64"))
    assertTrue(masked.startsWith("2001:db8:85a3"))
  }
}
