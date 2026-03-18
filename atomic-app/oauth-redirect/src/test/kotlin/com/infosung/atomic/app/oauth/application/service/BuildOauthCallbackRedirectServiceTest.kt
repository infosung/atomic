package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.OauthRedirectClientTarget
import com.infosung.atomic.app.oauth.OauthRelayPayload
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderTokenExchange
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.springframework.security.oauth2.jwt.Jwt

class BuildOauthCallbackRedirectServiceTest {
  @Test
  fun `build should return mobile deep link redirect with relayCode`() {
    val relayPort = FakeIssueOauthRelayCodePort()
    val service =
        BuildOauthCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    jwt =
                        stateJwt(
                            provider = "GOOGLE",
                            redirectUri = "myapp://oauth/callback",
                            callbackBindingKey = "atomicCallbackBinding",
                            callbackBindingToken = "binding-token",
                        ),
                ),
            issueOauthRelayCodePort = relayPort,
            validateOauthRedirectUriPort =
                FakeValidateOauthRedirectUriPort("myapp://oauth/callback"),
            callbackBindingEnabled = true,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
            relayCodeQueryParameterName = "relayCode",
        )

    val result =
        service.build(
            provider = "google",
            code = "code-123",
            state = "state-123",
            additionalParameters = mapOf("scope" to "profile"),
            callbackBindingToken = "binding-token",
        )

    assertEquals("myapp://oauth/callback?relayCode=relay-123", result.frontendRedirectUrl)
    assertEquals(OauthProviderName.GOOGLE, result.providerName)
    assertEquals(OauthRedirectClientTarget.APP_LINK, result.redirectTargetType)
    assertEquals(9, result.relayCodeLength)
    assertNotNull(relayPort.lastPayload)
    assertEquals(OauthProviderName.GOOGLE, relayPort.lastPayload!!.provider)
    assertEquals("access-token", relayPort.lastPayload!!.accessToken)
  }

  @Test
  fun `build should reject missing callback binding cookie token when enabled`() {
    val service =
        BuildOauthCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    jwt =
                        stateJwt(
                            provider = "GOOGLE",
                            redirectUri = "https://frontend.example.com/oauth/callback",
                            callbackBindingKey = "atomicCallbackBinding",
                            callbackBindingToken = "binding-token",
                        ),
                ),
            issueOauthRelayCodePort = FakeIssueOauthRelayCodePort(),
            validateOauthRedirectUriPort =
                FakeValidateOauthRedirectUriPort("https://frontend.example.com/oauth/callback"),
            callbackBindingEnabled = true,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
            relayCodeQueryParameterName = "relayCode",
        )

    val error =
        assertFailsWith<HttpStatusException> {
          service.build(
              provider = "google",
              code = "code-123",
              state = "state-123",
              additionalParameters = emptyMap(),
              callbackBindingToken = null,
          )
        }

    assertEquals(400, error.status)
    assertEquals("OAuth callback binding cookie is missing.", error.message)
  }

  private class FakeOauthProviderOperationsPort : OauthProviderOperationsPort {
    override fun exchangeCode(
        provider: String,
        request: OauthTokenExchangeRequest,
    ): OauthProviderTokenExchange {
      return OauthProviderTokenExchange(
          providerName = OauthProviderName.GOOGLE,
          tokenResult = OauthTokenResult(accessToken = "access-token"),
      )
    }
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
      return "relay-123"
    }
  }

  private class FakeValidateOauthRedirectUriPort(
      private val validatedRedirectUri: String,
  ) : ValidateOauthRedirectUriPort {
    override fun validateRedirectUri(redirectUri: String): String = validatedRedirectUri
  }

  private fun stateJwt(
      provider: String,
      redirectUri: String,
      callbackBindingKey: String,
      callbackBindingToken: String,
  ): Jwt {
    return Jwt.withTokenValue("state-jwt")
        .header("alg", "HS256")
        .claim("provider", provider)
        .claim("redirect_uri", redirectUri)
        .claim(
            "attributes",
            mapOf(callbackBindingKey to callbackBindingToken),
        )
        .build()
  }
}
