package com.infosung.atomic.crypto.base64

object Base64UrlCodec {
  fun encode(bytes: ByteArray): String =
      com.infosung.atomic.crypto.codec.Base64UrlCodec.encode(bytes)

  fun decode(value: String): ByteArray =
      com.infosung.atomic.crypto.codec.Base64UrlCodec.decode(value)
}
