package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.oauth.api.OauthProviderName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BuildAppleCallbackRedirectServiceTest {
  @Test
  fun `build should return desktop loopback redirect with relayCode`() {
    val relayPort = FakeIssueOauthRelayCodePort()
    val service =
        BuildAppleCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    verifiedState =
                        verifiedState(
                            redirectUri = "http://127.0.0.1:49152/oauth/callback",
                            callbackBindingKey = "atomicCallbackBinding",
                            callbackBindingToken = "binding-token",
                        ),
                ),
            issueOauthRelayCodePort = relayPort,
            validateOauthRedirectUriPort =
                FakeValidateOauthRedirectUriPort("http://127.0.0.1:49152/oauth/callback"),
            callbackBindingEnabled = true,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
            relayCodeQueryParameterName = "relayCode",
        )

    val result =
        service.build(
            state = "state-123",
            idToken = "id-token",
            code = "code-123",
            user = "{\"name\":{\"firstName\":\"Atomic\"}}",
            additionalParameters = mapOf("locale" to "ko-KR"),
            callbackBindingToken = "binding-token",
        )

    assertEquals(
        "http://127.0.0.1:49152/oauth/callback?relayCode=relay-apple",
        result.frontendRedirectUrl,
    )
    assertEquals(OauthProviderName.APPLE, result.providerName)
    assertEquals(OauthRedirectClientTarget.LOOPBACK, result.redirectTargetType)
    assertNotNull(relayPort.lastPayload)
    val raw = relayPort.lastPayload!!.raw
    assertEquals("id-token", relayPort.lastPayload!!.idToken)
    assertEquals("code-123", raw.get("code"))
    assertEquals("ko-KR", raw.get("locale"))
  }

  private class FakeOauthProviderOperationsPort : OauthProviderOperationsPort {
    override fun requireProviderName(provider: String): OauthProviderName = OauthProviderName.APPLE
  }

  private class FakeVerifyOauthStatePort(
      private val verifiedState: OauthVerifiedState,
  ) : VerifyOauthStatePort {
    override fun verifyState(
        signedState: String,
        expectedProvider: OauthProviderName,
    ): OauthVerifiedState = verifiedState
  }

  private class FakeIssueOauthRelayCodePort : IssueOauthRelayCodePort {
    var lastPayload: OauthRelayPayload? = null

    override fun issueRelayCode(payload: OauthRelayPayload): String {
      lastPayload = payload
      return "relay-apple"
    }
  }

  private class FakeValidateOauthRedirectUriPort(
      private val validatedRedirectUri: String,
  ) : ValidateOauthRedirectUriPort {
    override fun validateRedirectUri(redirectUri: String): String = validatedRedirectUri
  }

  private fun verifiedState(
      redirectUri: String,
      callbackBindingKey: String,
      callbackBindingToken: String,
  ): OauthVerifiedState {
    return OauthVerifiedState(
        provider = OauthProviderName.APPLE,
        redirectUri = redirectUri,
        attributes = mapOf(callbackBindingKey to callbackBindingToken),
    )
  }
}
