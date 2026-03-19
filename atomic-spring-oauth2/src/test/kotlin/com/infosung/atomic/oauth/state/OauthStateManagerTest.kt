package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OauthStateManagerTest {
  private val signingSecret = "s".repeat(32)

  @Test
  fun `issueState and verifyState should keep custom attributes`() {
    val now = Instant.parse("2026-02-25T00:00:00Z")
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    val state =
        manager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://example.com/callback",
            nonce = "nonce-1",
            attributes = mapOf("flow" to "mobile", "tenant" to "infosung"),
        )
    val stateClaims =
        manager.verifyStateClaims(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://example.com/callback",
            expectedNonce = "nonce-1",
        )

    assertEquals("atomic-test", stateClaims.issuer)
    assertEquals(OauthProviderName.GOOGLE, stateClaims.provider)
    assertEquals("https://example.com/callback", stateClaims.redirectUri)
    assertEquals("nonce-1", stateClaims.nonce)
    assertEquals("mobile", stateClaims.attributes["flow"])
    assertEquals("infosung", stateClaims.attributes["tenant"])
    assertTrue(stateClaims.expiresAt.isAfter(stateClaims.issuedAt))
  }

  @Test
  fun `legacy verifyState should continue exposing equivalent jwt claims`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
        )

    val state =
        manager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://example.com/callback",
            nonce = "nonce-1",
            attributes = mapOf("flow" to "mobile"),
        )
    val stateJwt =
        manager.verifyState(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://example.com/callback",
            expectedNonce = "nonce-1",
        )

    @Suppress("UNCHECKED_CAST")
    val attributes = stateJwt.claims["attributes"] as? Map<String, String>
    assertEquals("GOOGLE", stateJwt.claims["provider"])
    assertEquals("https://example.com/callback", stateJwt.claims["redirect_uri"])
    assertEquals("nonce-1", stateJwt.claims["nonce"])
    assertEquals("mobile", attributes?.get("flow"))
  }

  @Test
  fun `verifyState should fail when state token is tampered`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
        )
    val state = manager.issueState()
    val tampered = tamperJwtSignatureSegment(state)

    assertFailsWith<InvalidOauthStateException> { manager.verifyState(tampered) }
  }

  @Test
  fun `verifyState should fail after expiration`() {
    val issuedAt = Instant.parse("2026-02-25T00:00:00Z")
    val issueManager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            clock = Clock.fixed(issuedAt, ZoneOffset.UTC),
        )
    val state = issueManager.issueState()

    val verifyManager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            clock = Clock.fixed(issuedAt.plusSeconds(301), ZoneOffset.UTC),
        )

    assertFailsWith<InvalidOauthStateException> { verifyManager.verifyState(state) }
  }

  @Test
  fun `verifyState should fail when expected nonce is different`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
        )
    val state = manager.issueState(nonce = "nonce-1")

    assertFailsWith<InvalidOauthStateException> {
      manager.verifyState(
          signedState = state,
          expectedNonce = "nonce-2",
      )
    }
  }

  @Test
  fun `verifyState should consume once when store is enabled`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            store = InMemoryOauthStateStore(),
        )
    val state = manager.issueState()

    manager.verifyState(state)
    assertFailsWith<InvalidOauthStateException> { manager.verifyState(state) }
  }

  @Test
  fun `constructor should reject short signing secret`() {
    assertFailsWith<IllegalArgumentException> {
      OauthStateManager(
          signingSecret = "too-short",
          issuer = "atomic-test",
          ttlSeconds = 300,
      )
    }
  }

  @Test
  fun `issueState should fail when attributes are too large`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            maxAttributesBytes = 16,
        )

    assertFailsWith<InvalidOauthStateException> {
      manager.issueState(
          attributes = mapOf("flow" to "this-value-is-too-large"),
      )
    }
  }

  @Test
  fun `issueState should fail when token length exceeds limit`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            issuer = "atomic-test",
            ttlSeconds = 300,
            maxStateTokenLength = 60,
        )

    assertFailsWith<InvalidOauthStateException> {
      manager.issueState(
          attributes = mapOf("flow" to "mobile"),
      )
    }
  }

  @Test
  fun `in memory state store should cleanup expired entries`() {
    val now = Instant.parse("2026-02-25T00:00:00Z")
    val store =
        InMemoryOauthStateStore(
            clock = Clock.fixed(now, ZoneOffset.UTC),
            cleanupInterval = 1,
        )

    store.save(stateId = "expired", signedState = "token-expired", expiresAt = now.minusSeconds(1))
    assertEquals(1, store.currentSize())

    store.save(stateId = "fresh", signedState = "token-fresh", expiresAt = now.plusSeconds(30))
    assertEquals(1, store.currentSize())
    assertFalse(store.consume("expired", "token-expired"))
    assertTrue(store.consume("fresh", "token-fresh"))
    assertEquals(0, store.currentSize())
  }

  private fun tamperJwtSignatureSegment(jwt: String): String {
    val segments = jwt.split('.')
    check(segments.size == 3) { "JWT must have three compact segments." }
    val signature = segments[2]
    check(signature.isNotEmpty()) { "JWT signature segment must not be empty." }
    val replacement = if (signature.first() == 'A') 'B' else 'A'
    return "${segments[0]}.${segments[1]}.$replacement${signature.drop(1)}"
  }
}
