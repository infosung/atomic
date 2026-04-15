package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.aead.AesGcmAead
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AesGcmAeadTest {
  @Test
  fun `decrypt should wrap tamper failure as illegal argument`() {
    val key = ByteArray(32) { index -> (index + 1).toByte() }
    val aead = AesGcmAead(key)
    val ciphertext = aead.encrypt("atomic-crypto".encodeToByteArray())
    val tampered = ciphertext.copy(ciphertext = ciphertext.ciphertext + 1)

    assertFailsWith<IllegalArgumentException> { aead.decrypt(tampered) }
  }
}
