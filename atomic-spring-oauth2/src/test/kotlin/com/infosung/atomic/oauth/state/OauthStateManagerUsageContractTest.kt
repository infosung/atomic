package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OauthStateManagerUsageContractTest {
  private val signingSecret = "s".repeat(32)

  @Test
  fun `readState should not consume one time state before verifyState`() {
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

    val readJwt =
        manager.readState(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://app.example.com/oauth/callback",
            expectedNonce = "nonce-1",
        )
    val verifiedJwt =
        manager.verifyState(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = "https://app.example.com/oauth/callback",
            expectedNonce = "nonce-1",
        )

    assertEquals("GOOGLE", readJwt.claims["provider"])
    assertEquals("mobile", (verifiedJwt.claims["attributes"] as Map<*, *>)["flow"])
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
          tooManyAttributesManager.issueState(attributes = mapOf("flow" to "mobile", "device" to "ios"))
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
}
