package com.infosung.atomic.contract.security

import java.net.InetAddress

/** Masks IPv4/IPv6 addresses for logging/privacy use cases. */
object IpMasker {
  /**
   * Masks host portion of IP address.
   *
   * Behavior:
   * - IPv4 -> `/24` masked value (last octet zeroed)
   * - IPv6 -> `/64` masked value (last 64 bits zeroed)
   * - Invalid/unrecognized input -> original input unchanged
   */
  fun mask(ip: String): String {
    if (ip.isBlank()) return ip

    if (!ip.contains('.') && !ip.contains(':')) return ip

    return try {
      val address = InetAddress.getByName(ip)
      val bytes = address.address

      when (bytes.size) {
        4 -> {
          bytes[3] = 0
          val maskedAddress = InetAddress.getByAddress(bytes)
          "${maskedAddress.hostAddress}/24"
        }
        16 -> {
          for (i in 8..15) {
            bytes[i] = 0
          }
          "${formatIPv6Address(bytes)}/64"
        }
        else -> ip
      }
    } catch (_: Exception) {
      ip
    }
  }

  private fun formatIPv6Address(bytes: ByteArray): String {
    require(bytes.size == 16) { "IPv6 address must be 16 bytes." }

    val hextets =
        ShortArray(8) { i ->
          (((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)).toShort()
        }

    var bestStart = -1
    var bestLen = 0
    var currentStart = -1
    var currentLen = 0

    for (i in hextets.indices) {
      if (hextets[i].toInt() == 0) {
        if (currentStart == -1) currentStart = i
        currentLen++
      } else {
        if (currentLen > bestLen) {
          bestStart = currentStart
          bestLen = currentLen
        }
        currentStart = -1
        currentLen = 0
      }
    }
    if (currentLen > bestLen) {
      bestStart = currentStart
      bestLen = currentLen
    }

    val parts = mutableListOf<String>()
    var i = 0
    while (i < hextets.size) {
      if (i == bestStart) {
        parts.add("")
        i += bestLen
        if (i == hextets.size) parts.add("")
        continue
      }
      parts.add((hextets[i].toInt() and 0xFFFF).toString(16))
      i++
    }

    if (parts.size == 2) return parts.joinToString("::")
    return parts.joinToString(":").replace(":::", "::")
  }
}
