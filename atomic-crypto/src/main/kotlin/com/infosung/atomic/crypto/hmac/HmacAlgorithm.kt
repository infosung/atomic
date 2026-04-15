package com.infosung.atomic.crypto.hmac

enum class HmacAlgorithm(val jcaName: String) {
  SHA256("HmacSHA256"),
  SHA384("HmacSHA384"),
  SHA512("HmacSHA512"),
}
