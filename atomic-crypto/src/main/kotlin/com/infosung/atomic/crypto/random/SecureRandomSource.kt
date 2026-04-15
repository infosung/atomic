package com.infosung.atomic.crypto.random

import java.security.SecureRandom

class SecureRandomSource(private val secureRandom: SecureRandom = SecureRandom()) {
  fun nextBytes(length: Int): ByteArray {
    require(length > 0) { "length must be greater than zero." }
    return ByteArray(length).also(secureRandom::nextBytes)
  }

  fun nextString(length: Int, alphabet: String = DEFAULT_ALPHABET): String {
    require(length > 0) { "length must be greater than zero." }
    require(alphabet.isNotEmpty()) { "alphabet must not be blank." }
    val chars = CharArray(length) { alphabet[secureRandom.nextInt(alphabet.length)] }
    return String(chars)
  }

  companion object {
    const val DEFAULT_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
  }
}
