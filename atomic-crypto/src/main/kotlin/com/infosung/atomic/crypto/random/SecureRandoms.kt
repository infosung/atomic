package com.infosung.atomic.crypto.random

object SecureRandoms {
  private val defaultSource = SecureRandomSource()

  fun nextBytes(length: Int): ByteArray = defaultSource.nextBytes(length)
}
