package com.infosung.atomic.crypto.codec

import java.nio.charset.StandardCharsets
import java.util.Base64

object Base64UrlCodec {
  fun encode(bytes: ByteArray): String =
      Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

  fun encode(text: String): String = encode(text.toByteArray(StandardCharsets.UTF_8))

  fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)

  fun decodeToString(value: String): String = String(decode(value), StandardCharsets.UTF_8)
}
