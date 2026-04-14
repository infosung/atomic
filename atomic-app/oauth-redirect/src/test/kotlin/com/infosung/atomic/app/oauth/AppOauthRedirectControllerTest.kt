package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectController
import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.app.oauth.autoconfigure.OauthRedirectComposition
import com.infosung.atomic.app.oauth.autoconfigure.OauthRelayCodeComposition
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.time.TimeProvider
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
import com.infosung.atomic.oauth.exception.HttpIOException
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import jakarta.servlet.http.Cookie
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
  fun `callback should map upstream provider io failure to 500 http status`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider =
        object : CapturingOauthProvider() {
          override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
            throw HttpIOException("Failed to exchange provider token.")
          }
        }
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )

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
                callbackBindingToken = "unused-binding-token",
            ),
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
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

    assertEquals(500, exception.status)
    assertEquals("Failed to exchange provider token.", exception.message)
  }

  @Test
  fun `redirect should reuse existing callback-binding cookie token`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    assertEquals("OAUTH_CALLBACK_BINDING_INVALID", exception.code)
    assertEquals("OAuth callback binding cookie is ambiguous.", exception.message)
    assertNull(response.getHeader("Set-Cookie"))
  }

  @Test
  fun `redirect should not set cookie when callback binding is disabled`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
  fun `redirect should reject client supplied pkce verifier parameter`() {
    val properties = configuredProperties()
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )

    val request = MockHttpServletRequest("GET", "/oauth/redirect/google")
    request.addParameter(
        "codeVerifier",
        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
    )
    val response = MockHttpServletResponse()

    val exception =
        assertFailsWith<HttpStatusException> {
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
        }

    assertEquals(400, exception.status)
    assertEquals("OAUTH_REDIRECT_INVALID_REQUEST", exception.code)
    assertEquals(
        "Client supplied PKCE parameter 'codeVerifier' is not supported on redirect endpoint. Use codeChallengeMethod only.",
        exception.message,
    )
  }

  @Test
  fun `redirect should set pkce verifier cookie when code challenge method is requested`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider =
        object : CapturingOauthProvider() {
          override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
            lastAuthorizationRequest = request
            return "https://provider.example.com/auth?state=state-for-pkce"
          }
        }
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
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
            codeChallengeMethod = "S256",
            request = request,
            response = response,
        )

    assertEquals("redirect:https://provider.example.com/auth?state=state-for-pkce", result)
    assertEquals(
        "true", provider.lastAuthorizationRequest?.stateAttributes?.get("__atomicPkceRequired"))
    assertEquals(
        com.infosung.atomic.oauth.api.OauthCodeChallengeMethod.S256,
        provider.lastAuthorizationRequest?.codeChallengeMethod,
    )
    val setCookieHeaders = response.getHeaders("Set-Cookie")
    assertTrue(setCookieHeaders.any { it.contains("${pkceCookieName("state-for-pkce")}=") })
  }

  @Test
  fun `callback should read pkce verifier cookie and clear it after success`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )

    `when`(
            stateManager.verifyStateClaims(
                "state-with-pkce",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(
            stateClaims(
                provider = "GOOGLE",
                redirectUri = "https://client.example.com/oauth/callback",
                attributes = mapOf("__atomicPkceRequired" to "true"),
            ),
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
    request.setCookies(
        Cookie(
            pkceCookieName("state-with-pkce"),
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        ),
    )
    val response = MockHttpServletResponse()

    val result =
        controller.callback(
            provider = "google",
            code = "code-value",
            state = "state-with-pkce",
            request = request,
            response = response,
        )

    assertTrue(result.startsWith("redirect:https://client.example.com/oauth/callback?relayCode="))
    assertEquals(
        "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        provider.lastExchangeRequest?.codeVerifier,
    )
    assertTrue(
        response.getHeaders("Set-Cookie").any {
          it.contains("${pkceCookieName("state-with-pkce")}=") && it.contains("Max-Age=0")
        },
    )
  }

  @Test
  fun `callback should reject ambiguous pkce verifier cookie`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = CapturingOauthProvider()
    val stateManager = mock(OauthStateManager::class.java)
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )

    `when`(
            stateManager.verifyStateClaims(
                "state-with-pkce",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(
            stateClaims(
                provider = "GOOGLE",
                redirectUri = "https://client.example.com/oauth/callback",
                attributes = mapOf("__atomicPkceRequired" to "true"),
            ),
        )

    val request = MockHttpServletRequest("GET", "/oauth/callback/google")
    request.setCookies(
        Cookie(
            pkceCookieName("state-with-pkce"),
            "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        ),
        Cookie(
            pkceCookieName("state-with-pkce"),
            "second-verifier-value-that-should-not-be-accepted",
        ),
    )
    val response = MockHttpServletResponse()

    val exception =
        assertFailsWith<HttpStatusException> {
          controller.callback(
              provider = "google",
              code = "code-value",
              state = "state-with-pkce",
              request = request,
              response = response,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("OAUTH_CALLBACK_INVALID_REQUEST", exception.code)
    assertEquals("OAuth PKCE verifier cookie is ambiguous.", exception.message)
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

  private fun newController(
      properties: AtomicAppOauthRedirectProperties,
      providers: List<OauthProvider>,
      stateManager: OauthStateManager,
  ): AppOauthRedirectController {
    val oauthServiceProvider = OauthServiceProvider(providers)
    val oauthProviderOperationsPort =
        OauthRedirectComposition.oauthProviderOperationsPort(oauthServiceProvider)
    val verifyOauthStatePort = OauthRedirectComposition.verifyOauthStatePort(stateManager)
    val relayCodeStorePort =
        OauthRelayCodeComposition.storeOauthRelayCodePort(InMemoryOauthRelayCodeStore())
    val issueOauthRelayCodeUseCase =
        OauthRelayCodeComposition.issueOauthRelayCodeUseCase(
            storeOauthRelayCodePort = relayCodeStorePort,
            properties = properties,
            timeProvider = TimeProvider(),
        )
    val issueOauthRelayCodePort =
        OauthRedirectComposition.issueOauthRelayCodePort(issueOauthRelayCodeUseCase)
    val validateOauthRedirectUriPort =
        OauthRedirectComposition.validateOauthRedirectUriPort(properties)
    return AppOauthRedirectController(
        buildAuthorizationRedirectUseCase =
            OauthRedirectComposition.buildAuthorizationRedirectUseCase(
                oauthProviderOperationsPort = oauthProviderOperationsPort,
                validateOauthRedirectUriPort = validateOauthRedirectUriPort,
                properties = properties,
            ),
        buildOauthCallbackRedirectUseCase =
            OauthRedirectComposition.buildOauthCallbackRedirectUseCase(
                oauthProviderOperationsPort = oauthProviderOperationsPort,
                verifyOauthStatePort = verifyOauthStatePort,
                issueOauthRelayCodePort = issueOauthRelayCodePort,
                validateOauthRedirectUriPort = validateOauthRedirectUriPort,
                properties = properties,
            ),
        buildAppleCallbackRedirectUseCase =
            OauthRedirectComposition.buildAppleCallbackRedirectUseCase(
                oauthProviderOperationsPort = oauthProviderOperationsPort,
                verifyOauthStatePort = verifyOauthStatePort,
                issueOauthRelayCodePort = issueOauthRelayCodePort,
                validateOauthRedirectUriPort = validateOauthRedirectUriPort,
                properties = properties,
            ),
        properties = properties,
    )
  }

  private fun stateClaims(
      provider: String,
      redirectUri: String,
      callbackBindingKey: String? = null,
      callbackBindingToken: String? = null,
      attributes: Map<String, String> = emptyMap(),
  ): OauthStateClaims {
    val now = Instant.now()
    return OauthStateClaims(
        issuer = "atomic-test",
        stateId = "state-token",
        issuedAt = now,
        expiresAt = now.plusSeconds(300),
        provider = OauthProviderName.valueOf(provider),
        redirectUri = redirectUri,
        attributes =
            buildMap {
              callbackBindingKey?.let { key ->
                callbackBindingToken?.let { token -> put(key, token) }
              }
              putAll(attributes)
            },
    )
  }

  private fun pkceCookieName(state: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(state.toByteArray(Charsets.UTF_8))
    val suffix = HexFormat.of().formatHex(digest, 0, 16)
    return "atomic_oauth_pkce_$suffix"
  }

  private open class CapturingOauthProvider : OauthProvider {
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
