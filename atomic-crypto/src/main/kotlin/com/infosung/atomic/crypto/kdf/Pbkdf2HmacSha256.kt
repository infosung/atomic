package com.infosung.atomic.crypto.kdf

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Pbkdf2HmacSha256 {
  fun deriveKey(
      password: CharArray,
      salt: ByteArray,
      iterations: Int,
      lengthBytes: Int,
  ): ByteArray {
    require(iterations > 0) { "iterations must be greater than zero." }
    require(lengthBytes > 0) { "lengthBytes must be greater than zero." }
    val spec = PBEKeySpec(password, salt, iterations, lengthBytes * 8)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  fun derive(
      secret: CharArray,
      salt: ByteArray,
      iterations: Int,
      length: Int,
  ): ByteArray =
      deriveKey(password = secret, salt = salt, iterations = iterations, lengthBytes = length)
}
