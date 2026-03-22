package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderAuthorization
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildAuthorizationRedirectServiceTest {
  @Test
  fun `build should validate redirect uri and include callback binding state attribute`() {
    val providerPort = FakeOauthProviderOperationsPort()
    val redirectUriPort =
        FakeValidateOauthRedirectUriPort(
            validatedRedirectUri = "myapp://oauth/callback",
        )
    val service =
        BuildAuthorizationRedirectService(
            oauthProviderOperationsPort = providerPort,
            validateOauthRedirectUriPort = redirectUriPort,
            callbackBindingEnabled = true,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
        )

    val result =
        service.build(
            provider = "google",
            redirectUri = "myapp://oauth/callback",
            nonce = "nonce-1",
            prompt = "consent",
            loginHint = "user@example.com",
            responseMode = "query",
            additionalParameters = mapOf("scope" to "profile"),
            callbackBindingToken = "binding-token",
        )

    assertEquals("https://provider.example.com/auth", result.authorizationUrl)
    assertEquals(OauthProviderName.GOOGLE, result.providerName)
    assertEquals(OauthRedirectClientTarget.APP_LINK, result.redirectTargetType)
    assertEquals("myapp://oauth/callback", providerPort.lastAuthorizationRequest!!.redirectUri)
    assertEquals(
        "binding-token",
        providerPort.lastAuthorizationRequest!!.stateAttributes["atomicCallbackBinding"],
    )
  }

  @Test
  fun `build should not require callback binding token when disabled`() {
    val providerPort = FakeOauthProviderOperationsPort()
    val service =
        BuildAuthorizationRedirectService(
            oauthProviderOperationsPort = providerPort,
            validateOauthRedirectUriPort =
                FakeValidateOauthRedirectUriPort("https://frontend.example.com/oauth/callback"),
            callbackBindingEnabled = false,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
        )

    val result =
        service.build(
            provider = "google",
            redirectUri = "https://frontend.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            additionalParameters = emptyMap(),
            callbackBindingToken = null,
        )

    assertEquals(OauthRedirectClientTarget.WEB, result.redirectTargetType)
    assertTrue(providerPort.lastAuthorizationRequest!!.stateAttributes.isEmpty())
  }

  @Test
  fun `build should reject missing callback binding token when enabled`() {
    val service =
        BuildAuthorizationRedirectService(
            oauthProviderOperationsPort = FakeOauthProviderOperationsPort(),
            validateOauthRedirectUriPort =
                FakeValidateOauthRedirectUriPort("https://frontend.example.com/oauth/callback"),
            callbackBindingEnabled = true,
            callbackBindingStateAttributeKey = "atomicCallbackBinding",
        )

    val error =
        assertFailsWith<OauthRedirectRequestException> {
          service.build(
              provider = "google",
              redirectUri = "https://frontend.example.com/oauth/callback",
              nonce = null,
              prompt = null,
              loginHint = null,
              responseMode = null,
              additionalParameters = emptyMap(),
              callbackBindingToken = null,
          )
        }

    assertEquals("OAuth callback binding token is required.", error.message)
    assertEquals(OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID, error.errorCode)
  }

  private class FakeOauthProviderOperationsPort : OauthProviderOperationsPort {
    var lastAuthorizationRequest: OauthAuthorizationRequest? = null

    override fun buildAuthorizationUrl(
        provider: String,
        request: OauthAuthorizationRequest,
    ): OauthProviderAuthorization {
      lastAuthorizationRequest = request
      return OauthProviderAuthorization(
          providerName = OauthProviderName.GOOGLE,
          authorizationUrl = "https://provider.example.com/auth",
      )
    }
  }

  private class FakeValidateOauthRedirectUriPort(
      private val validatedRedirectUri: String,
  ) : ValidateOauthRedirectUriPort {
    override fun validateRedirectUri(redirectUri: String): String = validatedRedirectUri
  }
}
