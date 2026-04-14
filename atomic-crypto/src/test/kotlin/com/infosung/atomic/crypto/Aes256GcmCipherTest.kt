package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.aead.Aes256GcmCipher
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Aes256GcmCipherTest {
  @Test
  fun `encrypt and decrypt should round-trip and reject tampering`() {
    val cipher = Aes256GcmCipher()
    val key = ByteArray(32) { index -> (index + 1).toByte() }
    val plaintext = "atomic-crypto".encodeToByteArray()

    val envelope = cipher.encrypt(plaintext, key)
    val decrypted = cipher.decrypt(envelope, key)

    assertContentEquals(plaintext, decrypted)

    val tampered = envelope.copy(payload = envelope.payload + "!")

    assertFailsWith<IllegalArgumentException> { cipher.decrypt(tampered, key) }
  }
}
