package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
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
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class AppOauthRedirectControllerHttpContractTest {
  @Test
  fun `redirect endpoint should return provider redirect and callback-binding cookie`() {
    val properties = configuredProperties()
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val controller = newController(properties = properties, providers = listOf(provider))
    val mockMvc = newMockMvc(controller = controller, properties = properties)

    val response =
        mockMvc
            .perform(
                get("/oauth/redirect/google")
                    .param("redirectUri", "https://app.example.com/oauth/callback"),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("https://provider.example.com/auth"))
            .andReturn()
            .response

    val setCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(setCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(setCookie.contains("HttpOnly"))
    assertTrue(setCookie.contains("Secure"))
    assertTrue(setCookie.contains("SameSite=${properties.callbackBinding.cookieSameSite}"))
    assertTrue(setCookie.contains("Path=${properties.callbackBinding.cookiePath}"))
    assertTrue(setCookie.contains("Max-Age=${properties.callbackBinding.cookieMaxAgeSeconds}"))
    val authorizationRequest = assertNotNull(provider.lastAuthorizationRequest)
    assertTrue(
        authorizationRequest.stateAttributes.containsKey(
            properties.callbackBinding.stateAttributeKey),
    )
  }

  @Test
  fun `configured redirect and callback paths should be honored`() {
    val properties =
        configuredProperties().apply {
          redirectEndpointPath = "/internal/oauth/redirect"
          callbackEndpointPath = "/internal/oauth/callback"
          callbackBinding.enabled = false
        }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
        )

    mockMvc
        .perform(
            get("/internal/oauth/redirect/google")
                .param("redirectUri", "https://app.example.com/oauth/callback"),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrl("https://provider.example.com/auth"))

    mockMvc
        .perform(
            get("/internal/oauth/callback/google")
                .param("code", "code-123")
                .param("state", state)
                .param("scope", "profile"),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))

    assertNotNull(provider.lastExchangeRequest)
    assertTrue(provider.lastExchangeRequest!!.additionalParameters.containsKey("scope"))

    mockMvc
        .perform(
            get("/oauth/redirect/google")
                .param("redirectUri", "https://app.example.com/oauth/callback"),
        )
        .andExpect(status().isNotFound)

    mockMvc
        .perform(get("/oauth/callback/google").param("code", "code-123").param("state", state))
        .andExpect(status().isNotFound)
  }

  @Test
  fun `callback should honor configured relayCode query parameter name`() {
    val properties =
        configuredProperties().apply {
          relayCodeQueryParameterName = "code"
          callbackBinding.enabled = false
        }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
        )

    mockMvc
        .perform(
            get("/oauth/callback/google").param("code", "code-123").param("state", state),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?code=*"))
  }

  @Test
  fun `callback should reject reused state with documented 400 envelope`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager =
        OauthStateManager(
            signingSecret = "0123456789abcdef0123456789abcdef",
            store = InMemoryOauthStateStore(),
        )
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
        )

    mockMvc
        .perform(
            get("/oauth/callback/google").param("code", "code-123").param("state", state),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))

    mockMvc
        .perform(
            get("/oauth/callback/google").param("code", "code-123").param("state", state),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("State token is already used, expired, or unknown."))
  }

  @Test
  fun `callback should clear callback-binding cookie after successful provider callback`() {
    val properties = configuredProperties()
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to "callback-binding-token-1",
                ),
        )

    val response =
        mockMvc
            .perform(
                get("/oauth/callback/google")
                    .param("code", "code-123")
                    .param("state", state)
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-1",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))
            .andReturn()
            .response

    val clearedCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(clearedCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(clearedCookie.contains("Max-Age=0"))
    assertTrue(clearedCookie.contains("Path=${properties.callbackBinding.cookiePath}"))
  }

  @Test
  fun `redirect endpoint should return provider redirect and callback-binding cookie for mobile deep link`() {
    val properties =
        configuredProperties().apply { allowedRedirectUriPrefixes = listOf("myapp://oauth") }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val controller = newController(properties = properties, providers = listOf(provider))
    val mockMvc = newMockMvc(controller = controller, properties = properties)

    val response =
        mockMvc
            .perform(
                get("/oauth/redirect/google").param("redirectUri", "myapp://oauth/callback"),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("https://provider.example.com/auth"))
            .andReturn()
            .response

    val setCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(setCookie.contains("${properties.callbackBinding.cookieName}="))
  }

  @Test
  fun `callback should clear callback-binding cookie after successful mobile deep link callback`() {
    val properties =
        configuredProperties().apply { allowedRedirectUriPrefixes = listOf("myapp://oauth") }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "myapp://oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to "callback-binding-token-app",
                ),
        )

    val response =
        mockMvc
            .perform(
                get("/oauth/callback/google")
                    .param("code", "code-123")
                    .param("state", state)
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-app",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("myapp://oauth/callback?relayCode=*"))
            .andReturn()
            .response

    val clearedCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(clearedCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(clearedCookie.contains("Max-Age=0"))
  }

  @Test
  fun `callback should preserve callback-binding cookie after mobile deep link success in relaxed mode`() {
    val properties =
        configuredProperties().apply {
          allowedRedirectUriPrefixes = listOf("myapp://oauth")
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED
        }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "myapp://oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to
                        "callback-binding-token-app-relaxed",
                ),
        )

    val response =
        mockMvc
            .perform(
                get("/oauth/callback/google")
                    .param("code", "code-123")
                    .param("state", state)
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-app-relaxed",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("myapp://oauth/callback?relayCode=*"))
            .andReturn()
            .response

    assertTrue(response.getHeader(HttpHeaders.SET_COOKIE) == null)
  }

  @Test
  fun `callback should clear callback-binding cookie after successful desktop loopback callback`() {
    val properties =
        configuredProperties().apply {
          allowedRedirectUriPrefixes = listOf("http://127.0.0.1:49152/oauth")
        }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "http://127.0.0.1:49152/oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to
                        "callback-binding-token-loopback",
                ),
        )

    val response =
        mockMvc
            .perform(
                get("/oauth/callback/google")
                    .param("code", "code-123")
                    .param("state", state)
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-loopback",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(
                redirectedUrlPattern("http://127.0.0.1:49152/oauth/callback?relayCode=*"),
            )
            .andReturn()
            .response

    val clearedCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(clearedCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(clearedCookie.contains("Max-Age=0"))
  }

  @Test
  fun `callback should preserve callback-binding cookie after success in relaxed mode`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED
        }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(provider),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://app.example.com/oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to
                        "callback-binding-token-relaxed",
                ),
        )

    val response =
        mockMvc
            .perform(
                get("/oauth/callback/google")
                    .param("code", "code-123")
                    .param("state", state)
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-relaxed",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))
            .andReturn()
            .response

    assertTrue(response.getHeader(HttpHeaders.SET_COOKIE) == null)
  }

  @Test
  fun `apple get callback should return documented 400 error envelope`() {
    val properties = configuredProperties()
    val controller = newController(properties = properties, providers = listOf())
    val mockMvc = newMockMvc(controller = controller, properties = properties)

    mockMvc
        .perform(get("/oauth/callback/apple"))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("Apple callback supports POST form_post only."))
  }

  @Test
  fun `redirect should not set callback cookie when callback binding is disabled`() {
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val controller = newController(properties = properties, providers = listOf(provider))
    val mockMvc = newMockMvc(controller = controller, properties = properties)

    val response =
        mockMvc
            .perform(
                get("/oauth/redirect/google")
                    .param("redirectUri", "https://app.example.com/oauth/callback"),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("https://provider.example.com/auth"))
            .andReturn()
            .response

    assertTrue(response.getHeader(HttpHeaders.SET_COOKIE) == null)
  }

  @Test
  fun `redirect should return documented 400 envelope for disallowed redirectUri`() {
    val properties = configuredProperties()
    val provider = ContractOauthProvider(providerName = OauthProviderName.GOOGLE)
    val controller = newController(properties = properties, providers = listOf(provider))
    val mockMvc = newMockMvc(controller = controller, properties = properties)

    mockMvc
        .perform(
            get("/oauth/redirect/google")
                .param("redirectUri", "https://evil.example.com/oauth/callback"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("redirectUri is not allowed."))
  }

  @Test
  fun `apple post callback should redirect frontend with relayCode`() {
    val properties =
        configuredProperties().apply {
          callbackEndpointPath = "/internal/oauth/callback"
          callbackBinding.enabled = false
        }
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(ContractOauthProvider(providerName = OauthProviderName.APPLE)),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.APPLE,
            redirectUri = "https://app.example.com/oauth/callback",
        )

    mockMvc
        .perform(
            post("/internal/oauth/callback/apple")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("state", state)
                .param("id_token", "id-token")
                .param("code", "code-123")
                .param("user", "{\"name\":{\"firstName\":\"Atomic\"}}")
                .param("locale", "ko-KR"),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))

    mockMvc.perform(post("/oauth/callback/apple")).andExpect(status().isNotFound)
  }

  @Test
  fun `apple post callback should clear callback-binding cookie after success`() {
    val properties = configuredProperties()
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(ContractOauthProvider(providerName = OauthProviderName.APPLE)),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.APPLE,
            redirectUri = "https://app.example.com/oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to "callback-binding-token-apple",
                ),
        )

    val response =
        mockMvc
            .perform(
                post("/oauth/callback/apple")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("state", state)
                    .param("id_token", "id-token")
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-apple",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))
            .andReturn()
            .response

    val clearedCookie = assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE))
    assertTrue(clearedCookie.contains("${properties.callbackBinding.cookieName}="))
    assertTrue(clearedCookie.contains("Max-Age=0"))
    assertTrue(clearedCookie.contains("Path=${properties.callbackBinding.cookiePath}"))
  }

  @Test
  fun `apple post callback should preserve callback-binding cookie after success in relaxed mode`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.mode = AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED
        }
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(ContractOauthProvider(providerName = OauthProviderName.APPLE)),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.APPLE,
            redirectUri = "https://app.example.com/oauth/callback",
            attributes =
                mapOf(
                    properties.callbackBinding.stateAttributeKey to
                        "callback-binding-token-apple-relaxed",
                ),
        )

    val response =
        mockMvc
            .perform(
                post("/oauth/callback/apple")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("state", state)
                    .param("id_token", "id-token")
                    .cookie(
                        jakarta.servlet.http.Cookie(
                            properties.callbackBinding.cookieName,
                            "callback-binding-token-apple-relaxed",
                        ),
                    ),
            )
            .andExpect(status().isFound)
            .andExpect(redirectedUrlPattern("https://app.example.com/oauth/callback?relayCode=*"))
            .andReturn()
            .response

    assertTrue(response.getHeader(HttpHeaders.SET_COOKIE) == null)
  }

  @Test
  fun `apple post callback should support mobile deep link handoff with relayCode`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.enabled = false
          allowedRedirectUriPrefixes = listOf("myapp://oauth")
        }
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(ContractOauthProvider(providerName = OauthProviderName.APPLE)),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.APPLE,
            redirectUri = "myapp://oauth/callback",
        )

    mockMvc
        .perform(
            post("/oauth/callback/apple")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("state", state)
                .param("id_token", "id-token"),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("myapp://oauth/callback?relayCode=*"))
  }

  @Test
  fun `apple post callback should support desktop loopback handoff with relayCode`() {
    val properties =
        configuredProperties().apply {
          callbackBinding.enabled = false
          allowedRedirectUriPrefixes = listOf("http://127.0.0.1:49152/oauth")
        }
    val stateManager = stateManager()
    val controller =
        newController(
            properties = properties,
            providers = listOf(ContractOauthProvider(providerName = OauthProviderName.APPLE)),
            stateManager = stateManager,
        )
    val mockMvc = newMockMvc(controller = controller, properties = properties)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.APPLE,
            redirectUri = "http://127.0.0.1:49152/oauth/callback",
        )

    mockMvc
        .perform(
            post("/oauth/callback/apple")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("state", state)
                .param("id_token", "id-token"),
        )
        .andExpect(status().isFound)
        .andExpect(redirectedUrlPattern("http://127.0.0.1:49152/oauth/callback?relayCode=*"))
  }

  private fun configuredProperties(): AtomicAppOauthRedirectProperties {
    return AtomicAppOauthRedirectProperties().apply {
      allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
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
      stateManager: OauthStateManager = stateManager(),
  ): AppOauthRedirectController {
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = OauthServiceProvider(providers),
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    return AppOauthRedirectController(
        appOauthRedirectService = service,
        properties = properties,
    )
  }

  private fun stateManager(): OauthStateManager {
    return OauthStateManager(
        signingSecret = "0123456789abcdef0123456789abcdef",
    )
  }

  private fun newMockMvc(
      controller: AppOauthRedirectController,
      properties: AtomicAppOauthRedirectProperties,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(TestHttpStatusExceptionHandler())
        .addPlaceholderValue(
            "atomic.app.oauth.redirect.redirect-endpoint-path",
            properties.redirectEndpointPath,
        )
        .addPlaceholderValue(
            "atomic.app.oauth.redirect.callback-endpoint-path",
            properties.callbackEndpointPath,
        )
        .build()
  }

  private class ContractOauthProvider(
      override val providerName: OauthProviderName,
  ) : OauthProvider {
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

    override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult =
        OauthTokenResult()

    override fun revokeToken(request: OauthTokenRevokeRequest) = Unit

    override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
      return OauthIdentityResult(provider = providerName, userId = "user-1")
    }
  }

  @RestControllerAdvice
  private class TestHttpStatusExceptionHandler {
    @ExceptionHandler(HttpStatusException::class)
    fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
      return ResponseEntity.status(e.status).body(BaseResponse.error(e))
    }
  }
}
