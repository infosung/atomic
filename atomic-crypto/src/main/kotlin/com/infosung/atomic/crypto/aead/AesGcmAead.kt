package com.infosung.atomic.crypto.aead

import com.infosung.atomic.crypto.random.SecureRandomSource
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmAead(
    private val key: ByteArray,
    private val secureRandomSource: SecureRandomSource = SecureRandomSource(),
) {
  init {
    require(key.size == KEY_SIZE_BYTES) { "AES-256-GCM key must be 32 bytes." }
  }

  fun encrypt(
      plaintext: ByteArray,
      associatedData: ByteArray = ByteArray(0),
  ): AesGcmCiphertext {
    val iv = secureRandomSource.nextBytes(NONCE_SIZE_BYTES)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_BITS, iv))
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    return AesGcmCiphertext(iv = iv, ciphertext = cipher.doFinal(plaintext))
  }

  fun decrypt(
      ciphertext: AesGcmCiphertext,
      associatedData: ByteArray = ByteArray(0),
  ): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, ALGORITHM),
        GCMParameterSpec(TAG_BITS, ciphertext.iv),
    )
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    return try {
      cipher.doFinal(ciphertext.ciphertext)
    } catch (e: GeneralSecurityException) {
      throw GeneralSecurityException("Failed to decrypt AES-256-GCM ciphertext.", e)
    }
  }

  private companion object {
    const val ALGORITHM = "AES"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val KEY_SIZE_BYTES = 32
    const val NONCE_SIZE_BYTES = 12
    const val TAG_BITS = 128
  }
}
