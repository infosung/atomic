package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderIdentityResolution
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderTokenExchange
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildOauthCallbackRedirectServiceTest {
  @Test
  fun `build should return mobile deep link redirect with relayCode`() {
    val relayPort = FakeIssueOauthRelayCodePort()
    val oauthProviderOperationsPort = FakeOauthProviderOperationsPort()
    val service =
        BuildOauthCallbackRedirectService(
            oauthProviderOperationsPort = oauthProviderOperationsPort,
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    verifiedState =
                        verifiedState(
                            provider = OauthProviderName.GOOGLE,
                            redirectUri = "myapp://oauth/callback",
                            callbackBindingKey = "atomicCallbackBinding",
                            callbackBindingToken = "binding-token",
                            pkceRequired = true,
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
            codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        )

    assertEquals("myapp://oauth/callback?relayCode=relay-123", result.frontendRedirectUrl)
    assertEquals(OauthProviderName.GOOGLE, result.providerName)
    assertEquals(OauthRedirectClientTarget.APP_LINK, result.redirectTargetType)
    assertEquals(9, result.relayCodeLength)
    assertNotNull(relayPort.lastPayload)
    assertEquals(OauthProviderName.GOOGLE, relayPort.lastPayload!!.provider)
    assertEquals("access-token", relayPort.lastPayload!!.accessToken)
    assertEquals(
        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        oauthProviderOperationsPort.lastExchangeRequest?.codeVerifier,
    )
    assertEquals("google-user", relayPort.lastPayload!!.resolvedIdentity!!.providerSubject)
    assertTrue("atomicCallbackBinding" !in relayPort.lastPayload!!.stateAttributes)
    assertTrue("__atomicPkceRequired" !in relayPort.lastPayload!!.stateAttributes)
  }

  @Test
  fun `build should reject missing callback binding cookie token when enabled`() {
    val service =
        BuildOauthCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    verifiedState =
                        verifiedState(
                            provider = OauthProviderName.GOOGLE,
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
        assertFailsWith<OauthRedirectRequestException> {
          service.build(
              provider = "google",
              code = "code-123",
              state = "state-123",
              additionalParameters = emptyMap(),
              callbackBindingToken = null,
              codeVerifier = null,
          )
        }

    assertEquals("OAuth callback binding cookie is missing.", error.message)
    assertEquals(OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID, error.errorCode)
  }

  @Test
  fun `build should reject missing pkce verifier when state requires it`() {
    val service =
        BuildOauthCallbackRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            verifyOauthStatePort =
                FakeVerifyOauthStatePort(
                    verifiedState =
                        verifiedState(
                            provider = OauthProviderName.GOOGLE,
                            redirectUri = "https://frontend.example.com/oauth/callback",
                            callbackBindingKey = "atomicCallbackBinding",
                            callbackBindingToken = "binding-token",
                            pkceRequired = true,
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
        assertFailsWith<OauthRedirectRequestException> {
          service.build(
              provider = "google",
              code = "code-123",
              state = "state-123",
              additionalParameters = emptyMap(),
              callbackBindingToken = "binding-token",
              codeVerifier = null,
          )
        }

    assertEquals("OAuth PKCE verifier cookie is missing or invalid.", error.message)
    assertEquals(OauthRedirectErrorCode.OAUTH_CALLBACK_INVALID_REQUEST, error.errorCode)
  }

  private class FakeOauthProviderOperationsPort : OauthProviderOperationsPort {
    var lastExchangeRequest: OauthTokenExchangeRequest? = null

    override fun exchangeCode(
        provider: String,
        request: OauthTokenExchangeRequest,
    ): OauthProviderTokenExchange {
      lastExchangeRequest = request
      return OauthProviderTokenExchange(
          providerName = OauthProviderName.GOOGLE,
          tokenResult =
              OauthTokenResult(
                  accessToken = "access-token",
                  idToken = "id-token",
              ),
      )
    }

    override fun resolveIdentity(
        provider: String,
        request: OauthIdentityRequest,
    ): OauthProviderIdentityResolution {
      assertEquals(OauthIdentityStrategy.ID_TOKEN, request.strategy)
      return OauthProviderIdentityResolution(
          providerName = OauthProviderName.GOOGLE,
          identityResult =
              OauthIdentityResult(
                  provider = OauthProviderName.GOOGLE,
                  userId = "google-user",
                  providerSubject = "google-user",
              ),
      )
    }
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
      return "relay-123"
    }
  }

  private class FakeValidateOauthRedirectUriPort(
      private val validatedRedirectUri: String,
  ) : ValidateOauthRedirectUriPort {
    override fun validateRedirectUri(redirectUri: String): String = validatedRedirectUri
  }

  private fun verifiedState(
      provider: OauthProviderName,
      redirectUri: String,
      callbackBindingKey: String,
      callbackBindingToken: String,
      pkceRequired: Boolean = false,
  ): OauthVerifiedState {
    return OauthVerifiedState(
        provider = provider,
        redirectUri = redirectUri,
        attributes =
            buildMap {
              put(callbackBindingKey, callbackBindingToken)
              if (pkceRequired) {
                put("__atomicPkceRequired", "true")
              }
            },
    )
  }
}
