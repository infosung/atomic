package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.state.OauthStateManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.oauth2.jwt.Jwt

class AppOauthRedirectServiceTest {
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
            oauthProvider.buildAuthorizationUrl(
                OauthAuthorizationRequest(
                    redirectUri = "https://client.example.com/login/callback",
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
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateJwt = stateJwt(provider = "GOOGLE", redirectUri = "https://client.example.com/oauth")
    `when`(oauthServiceProvider.getService("google")).thenReturn(oauthProvider)
    `when`(oauthProvider.providerName).thenReturn(OauthProviderName.GOOGLE)
    `when`(
            stateManager.readState(
                "state-value",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(stateJwt)
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
        )

    val relayCode = redirectUrl.substringAfter("relayCode=")
    val payload = relayCodeService.consumeRelayCode(relayCode)
    assertNotNull(payload)
    assertEquals(OauthProviderName.GOOGLE, payload.provider)
    assertEquals("access-token", payload.accessToken)
    assertEquals("id-token", payload.idToken)
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
            stateManager.readState(
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
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRedirectService(
            oauthServiceProvider = oauthServiceProvider,
            oauthStateManager = stateManager,
            relayCodeService = relayCodeService,
            properties = properties,
        )
    val stateJwt = stateJwt(provider = "APPLE", redirectUri = "https://client.example.com/oauth")
    `when`(oauthServiceProvider.getService("APPLE")).thenReturn(appleProvider)
    `when`(appleProvider.providerName).thenReturn(OauthProviderName.APPLE)
    `when`(stateManager.verifyState("state-value", OauthProviderName.APPLE, null, null))
        .thenReturn(stateJwt)

    val redirectUrl =
        service.buildAppleCallbackRedirectUrl(
            state = "state-value",
            idToken = "apple-id-token",
            code = "apple-code",
            user = "{\"name\":\"apple-user\"}",
            additionalParameters = mapOf("foo" to "bar"),
        )

    val relayCode = redirectUrl.substringAfter("relayCode=")
    val payload = relayCodeService.consumeRelayCode(relayCode)
    assertNotNull(payload)
    assertEquals(OauthProviderName.APPLE, payload.provider)
    assertEquals("apple-id-token", payload.idToken)
    assertEquals("apple-code", payload.raw["code"])
  }

  private fun stateJwt(
      provider: String,
      redirectUri: String,
  ): Jwt {
    val now = Instant.now()
    return Jwt(
        "state-token",
        now,
        now.plusSeconds(300),
        mapOf("alg" to "HS256"),
        mapOf(
            "provider" to provider,
            "redirect_uri" to redirectUri,
        ),
    )
  }
}
