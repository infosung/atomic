package com.infosung.atomic.contract.random

import java.security.SecureRandom

/**
 * Random utility helpers.
 */
object RandomUtil {
  private val randomChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
  private val secureRandom = SecureRandom()

  /**
   * Generates alphanumeric random text.
   *
   * @param length Desired output length. If null, a random length in `10..19` is used.
   */
  fun randomString(length: Int? = null): String {
    val size = length ?: (secureRandom.nextInt(10) + 10)
    return buildString(size) {
      repeat(size) { append(randomChars[secureRandom.nextInt(randomChars.size)]) }
    }
  }
}
