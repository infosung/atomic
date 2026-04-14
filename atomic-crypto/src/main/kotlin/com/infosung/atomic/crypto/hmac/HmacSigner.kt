package com.infosung.atomic.crypto.hmac

import com.infosung.atomic.crypto.codec.Base64UrlCodec
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class HmacSigner(
    private val algorithm: String,
    private val secretKey: SecretKey,
) {
  constructor(
      algorithm: HmacAlgorithm,
      secret: ByteArray,
  ) : this(algorithm.jcaName, SecretKeySpec(secret, algorithm.jcaName))

  fun sign(message: ByteArray): ByteArray = mac().doFinal(message)

  fun signBase64Url(message: ByteArray): String = Base64UrlCodec.encode(sign(message))

  fun verify(
      message: ByteArray,
      signature: ByteArray,
  ): Boolean = MessageDigest.isEqual(sign(message), signature)

  private fun mac(): Mac = Mac.getInstance(algorithm).apply { init(secretKey) }
}
