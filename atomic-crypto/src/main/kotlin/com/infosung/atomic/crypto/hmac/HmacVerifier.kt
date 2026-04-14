package com.infosung.atomic.crypto.hmac

import java.security.MessageDigest
import javax.crypto.SecretKey

class HmacVerifier(
    private val algorithm: String,
    private val candidateSecretKeys: List<SecretKey>,
) {
  init {
    require(candidateSecretKeys.isNotEmpty()) { "candidateSecretKeys must not be empty." }
  }

  fun verify(message: ByteArray, signature: ByteArray): Boolean =
      candidateSecretKeys.any { candidate ->
        MessageDigest.isEqual(HmacSigner(algorithm, candidate).sign(message), signature)
      }
}
