package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.kdf.HkdfSha256
import com.infosung.atomic.crypto.kdf.Pbkdf2HmacSha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KdfCryptoTest {
  @Test
  fun `hkdf should derive stable output length`() {
    val key =
        HkdfSha256.deriveKey(
            ikm = "input-key".toByteArray(),
            salt = byteArrayOf(1, 2, 3),
            info = byteArrayOf(4, 5),
            length = 42)

    assertEquals(42, key.size)
  }

  @Test
  fun `pbkdf2 should derive stable output length`() {
    val key =
        Pbkdf2HmacSha256.deriveKey(
            password = "password".toCharArray(),
            salt = byteArrayOf(1, 2, 3, 4),
            iterations = 1000,
            lengthBytes = 32,
        )

    assertEquals(32, key.size)
    assertContentEquals(
        key,
        Pbkdf2HmacSha256.deriveKey(
            password = "password".toCharArray(),
            salt = byteArrayOf(1, 2, 3, 4),
            iterations = 1000,
            lengthBytes = 32,
        ),
    )
  }
}
