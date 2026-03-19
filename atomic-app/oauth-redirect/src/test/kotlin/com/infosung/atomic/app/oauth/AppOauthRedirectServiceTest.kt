package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.port.`in`.AuthorizationRedirectResult
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.CallbackRedirectResult
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AppOauthRedirectServiceTest {
  @Test
  fun `buildAuthorizationRedirectUrl should map application exception to documented http 400`() {
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = mock(OauthServiceProvider::class.java),
            oauthStateManager = mock(OauthStateManager::class.java),
            relayCodeService =
                AppOauthRelayCodeService(
                    relayCodeStore = InMemoryOauthRelayCodeStore(),
                    properties = AtomicAppOauthRedirectProperties(),
                ),
            properties = AtomicAppOauthRedirectProperties(),
            buildAuthorizationRedirectUseCase =
                object : BuildAuthorizationRedirectUseCase {
                  override fun build(
                      provider: String,
                      redirectUri: String,
                      nonce: String?,
                      prompt: String?,
                      loginHint: String?,
                      responseMode: String?,
                      additionalParameters: Map<String, String>,
                      callbackBindingToken: String?,
                  ): AuthorizationRedirectResult {
                    throw OauthRedirectRequestException("redirectUri is invalid.")
                  }
                },
            buildOauthCallbackRedirectUseCase = unusedCallbackUseCase(),
            buildAppleCallbackRedirectUseCase = unusedAppleCallbackUseCase(),
        )

    val error =
        assertFailsWith<HttpStatusException> {
          service.buildAuthorizationRedirectUrl(
              provider = "google",
              redirectUri = "bad",
              nonce = null,
              prompt = null,
              loginHint = null,
              responseMode = null,
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, error.status)
    assertEquals("redirectUri is invalid.", error.message)
  }

  @Test
  fun `buildCallbackRedirectUrl should map application exception to documented http 400`() {
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = mock(OauthServiceProvider::class.java),
            oauthStateManager = mock(OauthStateManager::class.java),
            relayCodeService =
                AppOauthRelayCodeService(
                    relayCodeStore = InMemoryOauthRelayCodeStore(),
                    properties = AtomicAppOauthRedirectProperties(),
                ),
            properties = AtomicAppOauthRedirectProperties(),
            buildAuthorizationRedirectUseCase =
                object : BuildAuthorizationRedirectUseCase {
                  override fun build(
                      provider: String,
                      redirectUri: String,
                      nonce: String?,
                      prompt: String?,
                      loginHint: String?,
                      responseMode: String?,
                      additionalParameters: Map<String, String>,
                      callbackBindingToken: String?,
                  ): AuthorizationRedirectResult {
                    return AuthorizationRedirectResult(
                        providerName = OauthProviderName.GOOGLE,
                        authorizationUrl = "https://provider.example.com/auth",
                        redirectTargetType = OauthRedirectClientTarget.WEB,
                    )
                  }
                },
            buildOauthCallbackRedirectUseCase =
                object : BuildOauthCallbackRedirectUseCase {
                  override fun build(
                      provider: String,
                      code: String,
                      state: String,
                      additionalParameters: Map<String, String>,
                      callbackBindingToken: String?,
                  ): CallbackRedirectResult {
                    throw OauthRedirectRequestException("OAuth callback binding cookie is missing.")
                  }
                },
            buildAppleCallbackRedirectUseCase = unusedAppleCallbackUseCase(),
        )

    val error =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "google",
              code = "code-123",
              state = "state-123",
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, error.status)
    assertEquals("OAuth callback binding cookie is missing.", error.message)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should reject non-absolute redirectUri`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildAuthorizationRedirectUrl(
              provider = "google",
              redirectUri = "/oauth/callback",
              nonce = null,
              prompt = null,
              loginHint = null,
              responseMode = null,
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, exception.status)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should reject deceptive host even when starts with allowed prefix`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          allowedRedirectUriPrefixes = listOf("https://app.example.com")
        }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildAuthorizationRedirectUrl(
              provider = "google",
              redirectUri = "https://app.example.com.evil.com/callback",
              nonce = null,
              prompt = null,
              loginHint = null,
              responseMode = null,
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, exception.status)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should return 400 when allowed redirect prefix config is invalid`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth?bad=1")
        }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildAuthorizationRedirectUrl(
              provider = "google",
              redirectUri = "https://app.example.com/oauth/callback",
              nonce = null,
              prompt = null,
              loginHint = null,
              responseMode = null,
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, exception.status)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should allow same host and configured path prefix`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties =
        AtomicAppOauthRedirectProperties().apply {
          allowedRedirectUriPrefixes = listOf("https://app.example.com/oauth")
        }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            oauthProvider.buildAuthorizationUrl(
                OauthAuthorizationRequest(
                    redirectUri = "https://app.example.com/oauth/callback",
                    stateAttributes =
                        mapOf(
                            properties.callbackBinding.stateAttributeKey to CALLBACK_BINDING_TOKEN),
                    additionalParameters = emptyMap(),
                ),
            ),
        )
        .thenReturn("https://provider/auth")

    val result =
        service.buildAuthorizationRedirectUrl(
            provider = "google",
            redirectUri = "https://app.example.com/oauth/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            additionalParameters = emptyMap(),
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )

    assertEquals("https://provider/auth", result)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should return provider authorization url`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties =
        configuredProperties().apply {
          allowedRedirectUriPrefixes = listOf("https://client.example.com")
        }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            oauthProvider.buildAuthorizationUrl(
                OauthAuthorizationRequest(
                    redirectUri = "https://client.example.com/login/callback",
                    stateAttributes =
                        mapOf(
                            properties.callbackBinding.stateAttributeKey to CALLBACK_BINDING_TOKEN),
                    additionalParameters = emptyMap(),
                ),
            ),
        )
        .thenReturn("https://provider/auth")

    val result =
        service.buildAuthorizationRedirectUrl(
            provider = "google",
            redirectUri = "https://client.example.com/login/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            additionalParameters = emptyMap(),
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )

    assertEquals("https://provider/auth", result)
  }

  @Test
  fun `buildAuthorizationRedirectUrl should not require callback binding token when disabled`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties().apply { callbackBinding.enabled = false }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            oauthProvider.buildAuthorizationUrl(
                OauthAuthorizationRequest(
                    redirectUri = "https://client.example.com/login/callback",
                    stateAttributes = emptyMap(),
                    additionalParameters = emptyMap(),
                ),
            ),
        )
        .thenReturn("https://provider/auth")

    val result =
        service.buildAuthorizationRedirectUrl(
            provider = "google",
            redirectUri = "https://client.example.com/login/callback",
            nonce = null,
            prompt = null,
            loginHint = null,
            responseMode = null,
            additionalParameters = emptyMap(),
        )

    assertEquals("https://provider/auth", result)
  }

  @Test
  fun `buildCallbackRedirectUrl should return client redirect with relayCode and storable payload`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateClaims =
        stateClaims(
            provider = "GOOGLE",
            redirectUri = "https://client.example.com/oauth",
            callbackBindingKey = properties.callbackBinding.stateAttributeKey,
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateClaims)
    `when`(
            oauthProvider.exchangeCode(
                OauthTokenExchangeRequest(
                    code = "code-value",
                    state = "state-value",
                    additionalParameters = emptyMap(),
                ),
            ),
        )
        .thenReturn(
            OauthTokenResult(
                accessToken = "access-token",
                idToken = "id-token",
            ),
        )

    val redirectUrl =
        service.buildCallbackRedirectUrl(
            provider = "google",
            code = "code-value",
            state = "state-value",
            additionalParameters = emptyMap(),
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )

    val relayCode = redirectUrl.substringAfter("relayCode=")
    val payload = relayCodeService.consumeRelayCode(relayCode)
    assertNotNull(payload)
    assertEquals(OauthProviderName.GOOGLE, payload.provider)
    assertEquals("access-token", payload.accessToken)
    assertEquals("id-token", payload.idToken)
    verify(stateManager).verifyStateClaims("state-value", OauthProviderName.GOOGLE, null, null)
    verify(stateManager, never()).readState("state-value", OauthProviderName.GOOGLE, null, null)
  }

  @Test
  fun `buildCallbackRedirectUrl should return mobile deep link redirect with relayCode`() {
    assertCallbackRedirectUrlStartsWith(
        allowedRedirectUriPrefix = "myapp://oauth",
        redirectUri = "myapp://oauth/callback",
    )
  }

  @Test
  fun `buildCallbackRedirectUrl should return desktop loopback redirect with relayCode`() {
    assertCallbackRedirectUrlStartsWith(
        allowedRedirectUriPrefix = "http://127.0.0.1:49152/oauth",
        redirectUri = "http://127.0.0.1:49152/oauth/callback",
    )
  }

  private fun assertCallbackRedirectUrlStartsWith(
      allowedRedirectUriPrefix: String,
      redirectUri: String,
  ) {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties =
        configuredProperties().apply {
          allowedRedirectUriPrefixes = listOf(allowedRedirectUriPrefix)
        }
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateClaims =
        stateClaims(
            provider = "GOOGLE",
            redirectUri = redirectUri,
            callbackBindingKey = properties.callbackBinding.stateAttributeKey,
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateClaims)
    `when`(
            oauthProvider.exchangeCode(
                OauthTokenExchangeRequest(
                    code = "code-value",
                    state = "state-value",
                    additionalParameters = emptyMap(),
                ),
            ),
        )
        .thenReturn(OauthTokenResult(accessToken = "access-token"))

    val redirectUrl =
        service.buildCallbackRedirectUrl(
            provider = "google",
            code = "code-value",
            state = "state-value",
            additionalParameters = emptyMap(),
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )

    assertTrue(redirectUrl.startsWith("$redirectUri?relayCode="))
  }

  @Test
  fun `buildCallbackRedirectUrl should reject apple get callback`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("apple")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.APPLE)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "apple",
              code = "code",
              state = "state",
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, exception.status)
  }

  @Test
  fun `buildCallbackRedirectUrl should map callback argument errors to http 400`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenThrow(IllegalArgumentException("invalid state"))

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "google",
              code = "code-value",
              state = "state-value",
              additionalParameters = emptyMap(),
          )
        }

    assertEquals(400, exception.status)
    assertEquals("invalid state", exception.message)
  }

  @Test
  fun `buildAppleCallbackRedirectUrl should return client redirect with relayCode`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val appleProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateClaims =
        stateClaims(
            provider = "APPLE",
            redirectUri = "https://client.example.com/oauth",
            callbackBindingKey = properties.callbackBinding.stateAttributeKey,
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )
    `when`(oauthServiceProvider.getService("APPLE")).thenReturn(appleProvider)
    `when`(appleProvider.providerName).thenReturn(OauthProviderName.APPLE)
    `when`(stateManager.verifyStateClaims("state-value", OauthProviderName.APPLE, null, null))
        .thenReturn(stateClaims)

    val redirectUrl =
        service.buildAppleCallbackRedirectUrl(
            state = "state-value",
            idToken = "apple-id-token",
            code = "apple-code",
            user = "{\"name\":\"apple-user\"}",
            additionalParameters = mapOf("foo" to "bar"),
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )

    val relayCode = redirectUrl.substringAfter("relayCode=")
    val payload = relayCodeService.consumeRelayCode(relayCode)
    assertNotNull(payload)
    assertEquals(OauthProviderName.APPLE, payload.provider)
    assertEquals("apple-id-token", payload.idToken)
    assertEquals("apple-code", payload.raw["code"])
  }

  @Test
  fun `buildCallbackRedirectUrl should reject mismatched callback binding token`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateClaims =
        stateClaims(
            provider = "GOOGLE",
            redirectUri = "https://client.example.com/oauth",
            callbackBindingKey = properties.callbackBinding.stateAttributeKey,
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateClaims)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "google",
              code = "code-value",
              state = "state-value",
              additionalParameters = emptyMap(),
              callbackBindingToken = "wrong-binding-token",
          )
        }

    assertEquals(400, exception.status)
    assertEquals("OAuth callback binding token mismatch.", exception.message)
  }

  @Test
  fun `buildCallbackRedirectUrl should reject missing callback binding cookie token`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateClaims =
        stateClaims(
            provider = "GOOGLE",
            redirectUri = "https://client.example.com/oauth",
            callbackBindingKey = properties.callbackBinding.stateAttributeKey,
            callbackBindingToken = CALLBACK_BINDING_TOKEN,
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateClaims)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "google",
              code = "code-value",
              state = "state-value",
              additionalParameters = emptyMap(),
              callbackBindingToken = null,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("OAuth callback binding cookie is missing.", exception.message)
  }

  @Test
  fun `buildCallbackRedirectUrl should reject missing callback binding state attribute`() {
    val oauthServiceProvider = mock(OauthServiceProvider::class.java)
    val oauthProvider = mock(OauthProvider::class.java)
    val stateManager = mock(OauthStateManager::class.java)
    val relayCodeService =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )
    val properties = configuredProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val now = Instant.now()
    val stateClaims =
        OauthStateClaims(
            issuer = "atomic-test",
            stateId = "state-1",
            issuedAt = now,
            expiresAt = now.plusSeconds(300),
            provider = OauthProviderName.GOOGLE,
            redirectUri = "https://client.example.com/oauth",
            attributes = emptyMap(),
        )
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.verifyStateClaims(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateClaims)

    val exception =
        assertFailsWith<HttpStatusException> {
          service.buildCallbackRedirectUrl(
              provider = "google",
              code = "code-value",
              state = "state-value",
              additionalParameters = emptyMap(),
              callbackBindingToken = CALLBACK_BINDING_TOKEN,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("OAuth callback binding state is missing.", exception.message)
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

  private fun configuredProperties(): AtomicAppOauthRedirectProperties {
    return AtomicAppOauthRedirectProperties().apply {
      allowedRedirectUriPrefixes = listOf("https://app.example.com", "https://client.example.com")
    }
  }

  companion object {
    private const val CALLBACK_BINDING_TOKEN = "callback-binding-token"
  }

  private fun unusedCallbackUseCase(): BuildOauthCallbackRedirectUseCase {
    return object : BuildOauthCallbackRedirectUseCase {
      override fun build(
          provider: String,
          code: String,
          state: String,
          additionalParameters: Map<String, String>,
          callbackBindingToken: String?,
      ): CallbackRedirectResult {
        throw AssertionError("callback use-case should not be called in this test")
      }
    }
  }

  private fun unusedAppleCallbackUseCase(): BuildAppleCallbackRedirectUseCase {
    return object : BuildAppleCallbackRedirectUseCase {
      override fun build(
          state: String,
          idToken: String,
          code: String?,
          user: String?,
          additionalParameters: Map<String, String>,
          callbackBindingToken: String?,
      ): CallbackRedirectResult {
        throw AssertionError("apple callback use-case should not be called in this test")
      }
    }
  }
}
