package com.infosung.atomic.crypto.kdf

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HkdfSha256 {
  private const val HASH_LENGTH = 32
  private const val HMAC_ALGORITHM = "HmacSHA256"

  fun deriveKey(
      ikm: ByteArray,
      salt: ByteArray = ByteArray(0),
      info: ByteArray = ByteArray(0),
      length: Int,
  ): ByteArray {
    require(length > 0) { "length must be greater than zero." }
    require(length <= 255 * HASH_LENGTH) { "length must be at most 255 * 32 bytes." }

    val prk = extract(ikm = ikm, salt = salt)
    val output = ByteArray(length)
    var previous = ByteArray(0)
    var offset = 0
    var counter = 1

    while (offset < length) {
      val mac = Mac.getInstance(HMAC_ALGORITHM)
      mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
      if (previous.isNotEmpty()) {
        mac.update(previous)
      }
      if (info.isNotEmpty()) {
        mac.update(info)
      }
      mac.update(counter.toByte())
      previous = mac.doFinal()

      val copyLength = minOf(previous.size, length - offset)
      System.arraycopy(previous, 0, output, offset, copyLength)
      offset += copyLength
      counter += 1
    }

    return output
  }

  fun derive(
      ikm: ByteArray,
      salt: ByteArray = ByteArray(0),
      info: ByteArray = ByteArray(0),
      length: Int,
  ): ByteArray = deriveKey(ikm = ikm, salt = salt, info = info, length = length)

  private fun extract(ikm: ByteArray, salt: ByteArray): ByteArray {
    val normalizedSalt = if (salt.isNotEmpty()) salt else ByteArray(HASH_LENGTH)
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(normalizedSalt, HMAC_ALGORITHM))
    return mac.doFinal(ikm)
  }
}
