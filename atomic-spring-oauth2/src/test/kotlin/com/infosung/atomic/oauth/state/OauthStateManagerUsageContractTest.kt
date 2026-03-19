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

class OauthStateManagerUsageContractTest {
  private val signingSecret = "s".repeat(32)

  @Test
  fun `readStateClaims should not consume one time state before verifyStateClaims`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            store = InMemoryOauthStateStore(),
        )
    val state =
        manager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
            nonce = "nonce-1",
            attributes = mapOf("flow" to "mobile"),
        )

    val readState =
        manager.readStateClaims(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://app.example.com/oauth/callback",
            expectedNonce = "nonce-1",
        )
    val verifiedState =
        manager.verifyStateClaims(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://app.example.com/oauth/callback",
            expectedNonce = "nonce-1",
        )

    assertEquals(OauthProviderName.GOOGLE, readState.provider)
    assertEquals("mobile", verifiedState.attributes["flow"])
  }

  @Test
  fun `verifyState should reject mismatched provider redirectUri and nonce`() {
    val now = Instant.parse("2026-03-14T00:00:00Z")
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    val state =
        manager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
            nonce = "nonce-1",
        )

    assertFailsWith<InvalidOauthStateException> {
      manager.verifyState(signedState = state, expectedProvider = OauthProviderName.KAKAO)
    }
    assertFailsWith<InvalidOauthStateException> {
      manager.verifyState(
          signedState = state,
          expectedProvider = OauthProviderName.GOOGLE,
          expectedRedirectUri = "https://app.example.com/other/callback",
      )
    }
    assertFailsWith<InvalidOauthStateException> {
      manager.verifyState(
          signedState = state,
          expectedProvider = OauthProviderName.GOOGLE,
          expectedRedirectUri = "https://app.example.com/oauth/callback",
          expectedNonce = "nonce-2",
      )
    }
  }

  @Test
  fun `issueState should enforce documented attribute count and size limits`() {
    val tooManyAttributesManager =
        OauthStateManager(
            signingSecret = signingSecret,
            maxAttributesEntryCount = 1,
        )
    val tooLargeAttributesManager =
        OauthStateManager(
            signingSecret = signingSecret,
            maxAttributesBytes = 8,
        )

    val tooManyException =
        assertFailsWith<InvalidOauthStateException> {
          tooManyAttributesManager.issueState(
              attributes = mapOf("flow" to "mobile", "device" to "ios"))
        }
    val tooLargeException =
        assertFailsWith<InvalidOauthStateException> {
          tooLargeAttributesManager.issueState(attributes = mapOf("flow" to "mobile"))
        }

    assertEquals("Too many state attributes. Maximum count is 1.", tooManyException.message)
    assertEquals("State attributes are too large. Maximum bytes is 8.", tooLargeException.message)
  }

  @Test
  fun `issueState should reject overly long signed tokens`() {
    val manager =
        OauthStateManager(
            signingSecret = signingSecret,
            maxStateTokenLength = 10,
        )

    val exception =
        assertFailsWith<InvalidOauthStateException> {
          manager.issueState(
              provider = OauthProviderName.GOOGLE,
              redirectUri = "https://app.example.com/oauth/callback",
          )
        }

    assertEquals("State token is too long. Reduce state attributes.", exception.message)
  }

  @Test
  fun `replay protection capability should reflect backing store presence`() {
    val withoutStore = OauthStateManager(signingSecret = signingSecret)
    val withStore =
        OauthStateManager(
            signingSecret = signingSecret,
            store = InMemoryOauthStateStore(),
        )

    assertFalse(withoutStore.isReplayProtectionEnabled())
    assertTrue(withStore.isReplayProtectionEnabled())
  }
}
