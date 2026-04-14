package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.codec.Base64UrlCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class Base64UrlCodecTest {
  @Test
  fun `encode and decode should round-trip`() {
    val text = "atomic-crypto"
    val encoded = Base64UrlCodec.encode(text)

    assertEquals(text, Base64UrlCodec.decodeToString(encoded))
  }
}
