package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.aead.Aes256GcmCipher
import com.infosung.atomic.crypto.envelope.VersionedCryptoEnvelope
import com.infosung.atomic.crypto.envelope.VersionedCryptoEnvelopeCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class EnvelopeCryptoTest {
  @Test
  fun `versioned envelope codec should round-trip`() {
    val envelope = VersionedCryptoEnvelope(version = 1, algorithm = "test", payload = "payload")

    assertEquals(
        envelope,
        VersionedCryptoEnvelopeCodec.decode(VersionedCryptoEnvelopeCodec.encode(envelope)))
  }

  @Test
  fun `aes gcm cipher should round-trip through versioned envelope`() {
    val cipher = Aes256GcmCipher()
    val key = ByteArray(32) { index -> (index + 1).toByte() }
    val plaintext = "atomic-crypto".toByteArray()
    val encoded = cipher.encryptToString(plaintext = plaintext, key = key)

    val decrypted = cipher.decryptFromString(encoded, key = key)

    assertEquals("atomic-crypto", String(decrypted))
  }
}
