package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import jakarta.servlet.http.Cookie
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AppOauthRedirectControllerTest {
  @Test
  fun `redirect should issue callback-binding cookie and include token in state attributes`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    val response = MockHttpServletResponse()
    val result =
        controller.redirect(
            provider = "google",
            redirectUri = "https://client.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            request = request,
            response = response,
        )

    assertEquals("redirect:https://provider.example.com/auth", result)
    val authorizationRequest = provider.lastAuthorizationRequest
    assertNotNull(authorizationRequest)
    val bindingToken =
        authorizationRequest.stateAttributes[properties.callbackBinding.stateAttributeKey]
    assertNotNull(bindingToken)
    assertTrue(bindingToken.isNotBlank())

    val setCookie = response.getHeader("Set-Cookie")
    assertNotNull(setCookie)
    assertTrue(setCookie.contains("${properties.callbackBinding.cookieName}=$bindingToken"))
  }

  @Test
  fun `redirect should not issue callback-binding cookie when redirect validation fails`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    val response = MockHttpServletResponse()

    assertFailsWith<HttpStatusException> {
      controller.redirect(
          provider = "google",
          redirectUri = "https://evil.example.com/oauth/callback",
          nonce = null,
          prompt = null,
          loginHint = null,
          responseMode = null,
          request = request,
          response = response,
      )
    }

    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `callback should validate cookie binding without rewriting cookie`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val callbackBindingToken = "binding-token"
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(
            stateClaims(
                provider = "GOOGLE",
                redirectUri = "https://client.example.com/oauth/callback",
                callbackBindingKey = properties.callbackBinding.stateAttributeKey,
                callbackBindingToken = callbackBindingToken,
            ),
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
    request.setCookies(Cookie(properties.callbackBinding.cookieName, callbackBindingToken))
    val response = MockHttpServletResponse()
    val result =
        controller.callback(
            provider = "google",
            code = "code-value",
            state = "state-value",
            request = request,
            response = response,
        )

    assertTrue(result.startsWith("redirect:https://client.example.com/oauth/callback?relayCode="))
    assertNotNull(provider.lastExchangeRequest)
    assertEquals("code-value", provider.lastExchangeRequest?.code)
    assertEquals("state-value", provider.lastExchangeRequest?.state)

    val clearedCookie = response.getHeader("Set-Cookie")
    assertNotNull(clearedCookie)
    assertTrue(clearedCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(clearedCookie.contains("Max-Age=0"))
  }

  @Test
  fun `callback should preserve callback-binding cookie in relaxed mode`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED
        }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val callbackBindingToken = "binding-token"
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(
            stateClaims(
                provider = "GOOGLE",
                redirectUri = "https://client.example.com/oauth/callback",
                callbackBindingKey = properties.callbackBinding.stateAttributeKey,
                callbackBindingToken = callbackBindingToken,
            ),
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
    request.setCookies(Cookie(properties.callbackBinding.cookieName, callbackBindingToken))
    val response = MockHttpServletResponse()
    val result =
        controller.callback(
            provider = "google",
            code = "code-value",
            state = "state-value",
            request = request,
            response = response,
        )

    assertTrue(result.startsWith("redirect:https://client.example.com/oauth/callback?relayCode="))
    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `redirect should reuse existing callback-binding cookie token`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val existingToken = "existing-binding-token"
    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    request.setCookies(Cookie(properties.callbackBinding.cookieName, existingToken))
    val response = MockHttpServletResponse()
    val result =
        controller.redirect(
            provider = "google",
            redirectUri = "https://client.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            request = request,
            response = response,
        )

    assertEquals("redirect:https://provider.example.com/auth", result)
    val authorizationRequest = provider.lastAuthorizationRequest
    assertNotNull(authorizationRequest)
    assertEquals(
        existingToken,
        authorizationRequest.stateAttributes[properties.callbackBinding.stateAttributeKey],
    )
    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `callback should reject ambiguous callback-binding cookies`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
    request.setCookies(
        Cookie(properties.callbackBinding.cookieName, "first-token"),
        Cookie(properties.callbackBinding.cookieName, "second-token"),
    )
    val response = MockHttpServletResponse()

    val exception =
        assertFailsWith<HttpStatusException> {
          controller.callback(
              provider = "google",
              code = "code-value",
              state = "state-value",
              request = request,
              response = response,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("OAuth callback binding cookie is ambiguous.", exception.message)
    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `redirect should not set cookie when callback binding is disabled`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    val response = MockHttpServletResponse()
    val result =
        controller.redirect(
            provider = "google",
            redirectUri = "https://client.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            request = request,
            response = response,
        )

    assertEquals("redirect:https://provider.example.com/auth", result)
    assertTrue(provider.lastAuthorizationRequest?.stateAttributes.isNullOrEmpty())
    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `redirect should not set cookie when callback binding mode is disabled`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.DISABLED
        }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(listOf(provider)),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val controller =
        AppOauthRedirectController(
            appOauthRedirectService = service,
            properties = properties,
        )

    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    val response = MockHttpServletResponse()
    val result =
        controller.redirect(
            provider = "google",
            redirectUri = "https://client.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            request = request,
            response = response,
        )

    assertEquals("redirect:https://provider.example.com/auth", result)
    assertTrue(provider.lastAuthorizationRequest?.stateAttributes.isNullOrEmpty())
    assertNull(response.getHeader("Set-Cookie"))
  }

  private fun configuredProperties(): AtomicAppOauthRedirectProperties {
    return AtomicAppOauthRedirectProperties().apply {
      allowedRedirectUriPrefixes = listOf("https://client.example.com")
      callbackBinding.cookieName = "__Host-atomic_oauth_callback_binding"
      callbackBinding.cookieSameSite = "None"
      callbackBinding.cookiePath = "/"
      callbackBinding.cookieSecure = true
      callbackBinding.cookieMaxAgeSeconds = 600
    }
  }

  private fun stateClaims(
      provider: String,
      redirectUri: String,
      callbackBindingKey: String,
      callbackBindingToken: String,
  ): OauthStateClaims {
    val now = Instant.now()
    return OauthStateClaims(
        issuer = "atomic-test",
        stateId = "state-token",
        issuedAt = now,
        expiresAt = now.plusSeconds(300),
        provider = OauthProviderName.valueOf(provider),
        redirectUri = redirectUri,
        attributes = mapOf(callbackBindingKey to callbackBindingToken),
    )
  }

  private class CapturingOauthProvider : OauthProvider {
    override val providerName: OauthProviderName = OauthProviderName.GOOGLE
    var lastAuthorizationRequest: OauthAuthorizationRequest? = null
    var lastExchangeRequest: OauthTokenExchangeRequest? = null

    override fun capabilities(): Set<OauthProviderCapability> =
        setOf(
            OauthProviderCapability.AUTHORIZATION_URL,
            OauthProviderCapability.EXCHANGE_TOKEN,
        )

    override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
      lastAuthorizationRequest = request
      return "https://provider.example.com/auth"
    }

    override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
      lastExchangeRequest = request
      return OauthTokenResult(accessToken = "access-token")
    }

    override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
      throw UnsupportedOperationException("Not used in this test")
    }

    override fun revokeToken(request: OauthTokenRevokeRequest) {
      throw UnsupportedOperationException("Not used in this test")
    }

    override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
      throw UnsupportedOperationException("Not used in this test")
    }
  }
}
