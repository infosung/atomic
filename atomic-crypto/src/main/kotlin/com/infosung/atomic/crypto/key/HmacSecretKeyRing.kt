package com.infosung.atomic.crypto.key

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class HmacSecretKeyRing(
    val currentKey: String,
    val previousKeys: List<String> = emptyList(),
    val algorithm: String = "HmacSHA512",
) {
  init {
    require(currentKey.isNotBlank()) { "currentKey must not be blank." }
    previousKeys.forEachIndexed { index, key ->
      require(key.isNotBlank()) { "previousKeys[$index] must not be blank." }
    }
  }

  fun currentSecretKey(): SecretKey = toSecretKey(currentKey)

  fun candidateSecretKeys(): List<SecretKey> =
      (listOf(currentKey) + previousKeys).map(::toSecretKey)

  private fun toSecretKey(value: String): SecretKey {
    val encoded = Base64.getEncoder().encode(value.toByteArray(StandardCharsets.UTF_8))
    return SecretKeySpec(encoded, algorithm)
  }
}
