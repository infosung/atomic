package com.infosung.atomic.oauth.provider.google

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import com.infosung.atomic.oauth.api.OauthIdentityPayloadMode
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.exception.HttpJwtVerifyException
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.state.InvalidOauthStateException
import com.infosung.atomic.oauth.state.OauthStateManager
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.hamcrest.Matchers.containsString
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GoogleOauthProviderTest {
  @Test
  fun `capability matrix should match google behavior`() {
    val provider = createProvider()
    assertTrue(provider.supports(OauthProviderCapability.AUTHORIZATION_URL))
    assertTrue(provider.supports(OauthProviderCapability.EXCHANGE_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.REFRESH_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.REVOKE_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.AUTHORIZATION_PKCE_PLAIN))
    assertTrue(provider.supports(OauthProviderCapability.AUTHORIZATION_PKCE_S256))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE))
  }

  @Test
  fun `buildAuthorizationUrl should include defaults when scope is not provided`() {
    val stateManager = createStateManager()
    val provider = createProvider(stateManager = stateManager)
    val clientRedirectUri = "myapp://oauth/google/callback"

    val url =
        provider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                redirectUri = clientRedirectUri,
                prompt = "consent",
                codeChallenge = "challenge-value",
                codeChallengeMethod = OauthCodeChallengeMethod.S256,
            ),
        )

    assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
    assertTrue(url.contains("client_id=google-client"))
    assertTrue(url.contains("response_type=code"))
    assertTrue(url.contains("access_type=offline"))
    assertTrue(url.contains("scope="))
    assertTrue(url.contains("state="))
    assertTrue(
        url.contains("redirect_uri=https%3A%2F%2Fapi.example.com%2Foauth%2Fgoogle%2Fcallback"))
    assertTrue(url.contains("prompt=consent"))
    assertTrue(url.contains("code_challenge=challenge-value"))
    assertTrue(url.contains("code_challenge_method=S256"))

    val state = extractQueryParam(url, "state")
    val verified =
        stateManager.verifyState(
            signedState = state,
            expectedProvider = OauthProviderName.GOOGLE,
            expectedRedirectUri = clientRedirectUri,
        )
    assertEquals("GOOGLE", verified.claims["provider"])
  }

  @Test
  fun `buildAuthorizationUrl should ignore reserved additional parameter overrides`() {
    val provider = createProvider()

    val url =
        provider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                redirectUri = "myapp://oauth/google/callback",
                additionalParameters =
                    mapOf(
                        "client_id" to "evil-client",
                        "response_type" to "token",
                        "include_granted_scopes" to "true",
                    ),
            ),
        )

    assertTrue(url.contains("client_id=google-client"))
    assertTrue(!url.contains("client_id=evil-client"))
    assertTrue(url.contains("response_type=code"))
    assertTrue(!url.contains("response_type=token"))
    assertTrue(url.contains("include_granted_scopes=true"))
  }

  @Test
  fun `exchangeCode should map google token response`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "access_token": "access-token",
        "refresh_token": "refresh-token",
        "id_token": "id-token",
        "token_type": "Bearer",
        "expires_in": 3600,
        "scope": "openid email profile"
      }
      """
            .trimIndent()

    server
        .expect(ExpectedCount.once(), requestTo("https://oauth2.googleapis.com/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("grant_type=authorization_code")))
        .andExpect(content().string(containsString("code=auth-code")))
        .andExpect(content().string(containsString("code_verifier=verifier-value")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "redirect_uri=https%3A%2F%2Fapi.example.com%2Foauth%2Fgoogle%2Fcallback")),
        )
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))

    val stateManager = createStateManager()
    val provider = createProvider(restClient = builder.build(), stateManager = stateManager)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "myapp://oauth/google/callback",
        )

    val result =
        provider.exchangeCode(
            OauthTokenExchangeRequest(
                code = "auth-code",
                state = state,
                codeVerifier = "verifier-value",
                scopes = setOf("openid", "email"),
            ),
        )

    assertEquals("access-token", result.accessToken)
    assertEquals("refresh-token", result.refreshToken)
    assertEquals("id-token", result.idToken)
    assertEquals("Bearer", result.tokenType)
    assertEquals(3600, result.expiresInSeconds)
    assertEquals(setOf("openid", "email", "profile"), result.scopes)
    server.verify()
  }

  @Test
  fun `exchangeCode should fail when state is blank`() {
    val provider = createProvider()

    assertFailsWith<InvalidOauthRequestException> {
      provider.exchangeCode(
          OauthTokenExchangeRequest(
              code = "auth-code",
              state = " ",
          ),
      )
    }
  }

  @Test
  fun `exchangeCode should fail when state is invalid`() {
    val provider = createProvider()

    assertFailsWith<InvalidOauthStateException> {
      provider.exchangeCode(
          OauthTokenExchangeRequest(
              code = "auth-code",
              state = "invalid-state-token",
          ),
      )
    }
  }

  @Test
  fun `resolveIdentity with user info api should map profile fields`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "sub": "google-user",
        "email": "google@example.com",
        "email_verified": true,
        "name": "Google Tester",
        "given_name": "Google",
        "family_name": "Tester",
        "picture": "https://cdn.example.com/google.png",
        "hd": "example.com"
      }
      """
            .trimIndent()

    server
        .expect(ExpectedCount.once(), requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))

    val provider = createProvider(restClient = builder.build())

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.USER_INFO_API,
                accessToken = "access-token",
                scopes = setOf("openid", "email", "profile"),
            ),
        )

    assertEquals("google-user", identity.userId)
    assertEquals("google@example.com", identity.email)
    assertEquals("Google Tester", identity.displayName)
    assertEquals("https://cdn.example.com/google.png", identity.pictureUrl)
    assertEquals(setOf("openid", "email", "profile"), identity.scopes)
    server.verify()
  }

  @Test
  fun `resolveIdentity should support id-only payload mode`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "sub": "google-user",
        "email": "google@example.com",
        "name": "Google Tester"
      }
      """
            .trimIndent()

    server
        .expect(ExpectedCount.once(), requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))

    val provider = createProvider(restClient = builder.build())

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.USER_INFO_API,
                accessToken = "access-token",
                payloadMode = OauthIdentityPayloadMode.ID_ONLY,
            ),
        )

    assertEquals("google-user", identity.userId)
    assertNull(identity.email)
    assertEquals(mapOf("sub" to "google-user"), identity.claims)
    server.verify()
  }

  @Test
  fun `resolveIdentity should enforce configured client audience`() {
    val payload =
        GoogleIdToken.Payload()
            .setSubject("google-user")
            .setEmail("google@example.com")
            .setAudience("wrong-client")
            .setIssuer("https://accounts.google.com")
            .setExpirationTimeSeconds(9999999999)
            .setIssuedAtTimeSeconds(1)
    val token = GoogleIdToken(JsonWebSignature.Header(), payload, ByteArray(0), ByteArray(0))
    val provider = createProvider(verifier = StaticVerifier(token))

    assertFailsWith<HttpJwtVerifyException> {
      provider.resolveIdentity(
          OauthIdentityRequest(
              strategy = OauthIdentityStrategy.ID_TOKEN,
              idToken = "id-token",
          ),
      )
    }
  }

  @Test
  fun `buildAuthorizationUrl should reject unsupported scopes`() {
    val provider = createProvider(supportedScopes = setOf("openid", "email", "profile"))

    assertFailsWith<InvalidOauthRequestException> {
      provider.buildAuthorizationUrl(
          OauthAuthorizationRequest(
              redirectUri = "myapp://oauth/google/callback",
              scopes = setOf("openid", "https://www.googleapis.com/auth/youtube.readonly"),
          ),
      )
    }
  }

  @Test
  fun `auto strategy should fail without id token or access token`() {
    val provider = createProvider()

    assertFailsWith<InvalidOauthRequestException> {
      provider.resolveIdentity(
          OauthIdentityRequest(strategy = OauthIdentityStrategy.AUTO),
      )
    }
  }

  private fun createProvider(
      restClient: RestClient = RestClient.create(),
      supportedScopes: Set<String>? = null,
      stateManager: OauthStateManager = createStateManager(),
      verifier: GoogleIdTokenVerifier =
          GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
              .build(),
  ): GoogleOauthProvider {
    return GoogleOauthProvider(
        clientId = "google-client",
        clientSecret = "google-secret",
        serverRedirectUri = "https://api.example.com/oauth/google/callback",
        client = restClient,
        googleIdTokenVerifier = verifier,
        stateManager = stateManager,
        supportedScopes = supportedScopes,
    )
  }

  private fun createStateManager(): OauthStateManager {
    return OauthStateManager(
        signingSecret = "g".repeat(32),
        issuer = "google-state-test",
    )
  }

  private fun extractQueryParam(url: String, name: String): String {
    val query =
        URI(url).rawQuery ?: throw IllegalStateException("URL does not include query string.")
    val pairs = query.split("&")
    val prefix = "$name="
    val rawValue =
        pairs.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
            ?: throw IllegalStateException("Missing query parameter: $name")
    return URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
  }

  private class StaticVerifier(
      private val token: GoogleIdToken,
  ) : GoogleIdTokenVerifier(NetHttpTransport(), GsonFactory.getDefaultInstance()) {
    override fun verify(idTokenString: String?): GoogleIdToken = token
  }
}
