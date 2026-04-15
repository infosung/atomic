package com.infosung.atomic.crypto

import com.infosung.atomic.crypto.hmac.HmacSigner
import com.infosung.atomic.crypto.hmac.HmacVerifier
import com.infosung.atomic.crypto.key.HmacSecretKeyRing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HmacCryptoTest {
  @Test
  fun `verifier should accept signatures produced by current key`() {
    val ring = HmacSecretKeyRing(currentKey = "current-key", previousKeys = listOf("old-key"))
    val signer = HmacSigner(ring.algorithm, ring.currentSecretKey())
    val verifier = HmacVerifier(ring.algorithm, ring.candidateSecretKeys())
    val message = "hello-world".toByteArray()

    assertTrue(verifier.verify(message, signer.sign(message)))
  }

  @Test
  fun `verifier should accept signatures produced by previous key`() {
    val ring = HmacSecretKeyRing(currentKey = "current-key", previousKeys = listOf("old-key"))
    val signer =
        HmacSigner(
            ring.algorithm,
            HmacSecretKeyRing("old-key", algorithm = ring.algorithm).currentSecretKey())
    val verifier = HmacVerifier(ring.algorithm, ring.candidateSecretKeys())
    val message = "hello-world".toByteArray()

    assertTrue(verifier.verify(message, signer.sign(message)))
  }

  @Test
  fun `verifier should reject invalid signatures`() {
    val ring = HmacSecretKeyRing(currentKey = "current-key")
    val verifier = HmacVerifier(ring.algorithm, ring.candidateSecretKeys())

    assertFalse(verifier.verify("hello".toByteArray(), "world".toByteArray()))
  }
}
