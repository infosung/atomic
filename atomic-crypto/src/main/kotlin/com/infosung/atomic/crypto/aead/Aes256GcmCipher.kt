package com.infosung.atomic.crypto.aead

import com.infosung.atomic.crypto.codec.Base64UrlCodec
import com.infosung.atomic.crypto.envelope.VersionedCryptoEnvelope
import com.infosung.atomic.crypto.envelope.VersionedCryptoEnvelopeCodec
import com.infosung.atomic.crypto.random.SecureRandomSource
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Aes256GcmCipher(
    private val secureRandomSource: SecureRandomSource = SecureRandomSource(),
) {
  fun encrypt(
      plaintext: ByteArray,
      key: ByteArray,
      associatedData: ByteArray = ByteArray(0),
  ): VersionedCryptoEnvelope {
    require(key.size == 32) { "AES-256-GCM key must be 32 bytes." }
    val nonce = secureRandomSource.nextBytes(GCM_NONCE_BYTES)
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key, AES_ALGORITHM),
        GCMParameterSpec(GCM_TAG_BITS, nonce))
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    val ciphertext = cipher.doFinal(plaintext)
    return VersionedCryptoEnvelope(
        version = 1,
        algorithm = "AES-256-GCM",
        payload = "${Base64UrlCodec.encode(nonce)}.${Base64UrlCodec.encode(ciphertext)}",
    )
  }

  fun decrypt(
      envelope: VersionedCryptoEnvelope,
      key: ByteArray,
      associatedData: ByteArray = ByteArray(0),
  ): ByteArray {
    require(key.size == 32) { "AES-256-GCM key must be 32 bytes." }
    require(envelope.algorithm == "AES-256-GCM") {
      "Unsupported envelope algorithm: ${envelope.algorithm}"
    }
    val payloadParts = envelope.payload.split('.', limit = 2)
    require(payloadParts.size == 2) {
      "AES-256-GCM envelope payload must contain nonce and ciphertext."
    }

    val nonce = Base64UrlCodec.decode(payloadParts[0])
    val ciphertext = Base64UrlCodec.decode(payloadParts[1])
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, AES_ALGORITHM),
        GCMParameterSpec(GCM_TAG_BITS, nonce))
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    return try {
      cipher.doFinal(ciphertext)
    } catch (e: GeneralSecurityException) {
      throw IllegalArgumentException("Failed to decrypt AES-256-GCM envelope.", e)
    }
  }

  fun encryptToString(
      plaintext: ByteArray,
      key: ByteArray,
      associatedData: ByteArray = ByteArray(0),
  ): String = VersionedCryptoEnvelopeCodec.encode(encrypt(plaintext, key, associatedData))

  fun decryptFromString(
      value: String,
      key: ByteArray,
      associatedData: ByteArray = ByteArray(0),
  ): ByteArray = decrypt(VersionedCryptoEnvelopeCodec.decode(value), key, associatedData)

  companion object {
    private const val AES_ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
  }
}
