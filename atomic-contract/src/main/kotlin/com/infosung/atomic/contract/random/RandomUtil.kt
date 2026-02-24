package com.infosung.atomic.contract.random

import java.security.SecureRandom

object RandomUtil {
  private val randomChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
  private val secureRandom = SecureRandom()

  fun randomString(length: Int? = null): String {
    val size = length ?: (secureRandom.nextInt(10) + 10)
    return buildString(size) {
      repeat(size) { append(randomChars[secureRandom.nextInt(randomChars.size)]) }
    }
  }
}
