package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.OauthRedirectClientTarget
import com.infosung.atomic.app.oauth.OauthRelayPayload
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.oauth.api.OauthProviderName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.security.oauth2.jwt.Jwt

class BuildAppleCallbackRedirectServiceTest {
  @Test
  fun `build should return desktop loopback redirect with relayCode`() {
    val relayPort = FakeIssueOauthRelayCodePort()
    val service =
        BuildAppleCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    jwt =
                        stateJwt(
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
    assertEquals("id-token", relayPort.lastPayload!!.idToken)
    assertEquals("code-123", relayPort.lastPayload!!.raw?.get("code"))
    assertEquals("ko-KR", relayPort.lastPayload!!.raw?.get("locale"))
  }

  private class FakeOauthProviderOperationsPort : OauthProviderOperationsPort {
    override fun requireProviderName(provider: String): OauthProviderName = OauthProviderName.APPLE
  }

  private class FakeVerifyOauthStatePort(
      private val jwt: Jwt,
  ) : VerifyOauthStatePort {
    override fun verifyState(signedState: String, expectedProvider: OauthProviderName): Jwt = jwt
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

  private fun stateJwt(
      redirectUri: String,
      callbackBindingKey: String,
      callbackBindingToken: String,
  ): Jwt {
    return Jwt.withTokenValue("state-jwt")
        .header("alg", "HS256")
        .claim("provider", "APPLE")
        .claim("redirect_uri", redirectUri)
        .claim(
            "attributes",
            mapOf(callbackBindingKey to callbackBindingToken),
        )
        .build()
  }
}
