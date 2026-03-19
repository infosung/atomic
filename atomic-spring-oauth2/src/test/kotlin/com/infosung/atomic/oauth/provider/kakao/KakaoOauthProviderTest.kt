package com.infosung.atomic.oauth.provider.kakao

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityPayloadMode
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.exception.UnsupportedOauthOperationException
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.testsupport.JwtTestFixtures
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

class KakaoOauthProviderTest {
  @Test
  fun `capability matrix should match kakao behavior`() {
    val provider = createProvider()
    assertTrue(provider.supports(OauthProviderCapability.AUTHORIZATION_URL))
    assertTrue(provider.supports(OauthProviderCapability.EXCHANGE_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.REFRESH_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE))
    assertTrue(!provider.supports(OauthProviderCapability.REVOKE_TOKEN))
  }

  @Test
  fun `buildAuthorizationUrl should include default openid scope`() {
    val stateManager = createStateManager()
    val provider = createProvider(stateManager = stateManager)
    val clientRedirectUri = "myapp://oauth/kakao/callback"

    val url =
        provider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                redirectUri = clientRedirectUri,
                prompt = "login",
            ),
        )

    assertTrue(url.startsWith("https://kauth.kakao.com/oauth/authorize?"))
    assertTrue(url.contains("scope=openid"))
    assertTrue(url.contains("state="))
    assertTrue(
        url.contains("redirect_uri=https%3A%2F%2Fapi.example.com%2Foauth%2Fkakao%2Fcallback"))
    assertTrue(url.contains("prompt=login"))

    val state = extractQueryParam(url, "state")
    val verified =
        stateManager.verifyState(
            signedState = state,
            expectedProvider = OauthProviderName.KAKAO,
            expectedRedirectUri = clientRedirectUri,
        )
    assertEquals("KAKAO", verified.claims["provider"])
  }

  @Test
  fun `buildAuthorizationUrl should ignore reserved additional parameter overrides`() {
    val provider = createProvider()

    val url =
        provider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                additionalParameters =
                    mapOf(
                        "client_id" to "evil-client",
                        "response_type" to "token",
                        "foo" to "bar",
                    ),
            ),
        )

    assertTrue(url.contains("client_id=kakao-client"))
    assertTrue(!url.contains("client_id=evil-client"))
    assertTrue(url.contains("response_type=code"))
    assertTrue(!url.contains("response_type=token"))
    assertTrue(url.contains("foo=bar"))
  }

  @Test
  fun `exchangeCode should verify state and map kakao token response`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "token_type": "Bearer",
        "access_token": "access-token",
        "id_token": "id-token",
        "expires_in": 43199,
        "refresh_token": "refresh-token",
        "refresh_token_expires_in": 5184000,
        "scope": "openid,profile_nickname"
      }
      """
            .trimIndent()

    server
        .expect(ExpectedCount.once(), requestTo("https://kauth.kakao.com/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(containsString("grant_type=authorization_code")))
        .andExpect(content().string(containsString("code=auth-code")))
        .andExpect(
            content()
                .string(
                    containsString(
                        "redirect_uri=https%3A%2F%2Fapi.example.com%2Foauth%2Fkakao%2Fcallback")),
        )
        .andExpect(content().string(containsString("state=")))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))

    val stateManager = createStateManager()
    val provider = createProvider(restClient = builder.build(), stateManager = stateManager)
    val state =
        stateManager.issueState(
            provider = OauthProviderName.KAKAO,
            redirectUri = "myapp://oauth/kakao/callback",
        )

    val result =
        provider.exchangeCode(
            OauthTokenExchangeRequest(
                code = "auth-code",
                state = state,
            ),
        )

    assertEquals("access-token", result.accessToken)
    assertEquals("refresh-token", result.refreshToken)
    assertEquals("id-token", result.idToken)
    assertEquals("Bearer", result.tokenType)
    assertEquals(43199, result.expiresInSeconds)
    assertEquals(setOf("openid", "profile_nickname"), result.scopes)
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
  fun `resolveIdentity with id token should parse claims and invoke audience validator with request audience`() {
    val issuer = "https://kauth.kakao.com"
    val audience = "kakao-client"
    val kid = "kakao-key"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience, "kakao-native"),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audience,
            subject = "kakao-user",
            email = "kakao@example.com",
            additionalClaims = mapOf("nonce" to "nonce-1"),
        )

    var observedAud: String? = null
    val provider =
        KakaoOauthProvider(
            client = RestClient.create(),
            clientId = audience,
            clientSecret = "secret",
            serverRedirectUri = "https://api.example.com/oauth/kakao/callback",
            idTokenParser = parser,
            stateManager = createStateManager(),
            audValidator = { rawAud ->
              observedAud = rawAud
              audience
            },
        )

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.ID_TOKEN,
                idToken = token,
                audience = "kakao-native",
                nonce = "nonce-1",
            ),
        )

    assertEquals("kakao-native", observedAud)
    assertEquals("kakao-user", identity.userId)
    assertEquals("kakao@example.com", identity.email)
  }

  @Test
  fun `resolveIdentity with id token should require nonce by default`() {
    val issuer = "https://kauth.kakao.com"
    val audience = "kakao-client"
    val kid = "kakao-key"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audience,
            subject = "kakao-user",
        )

    val provider =
        KakaoOauthProvider(
            client = RestClient.create(),
            clientId = audience,
            clientSecret = "secret",
            serverRedirectUri = "https://api.example.com/oauth/kakao/callback",
            idTokenParser = parser,
            stateManager = createStateManager(),
        )

    assertFailsWith<InvalidOauthRequestException> {
      provider.resolveIdentity(
          OauthIdentityRequest(
              strategy = OauthIdentityStrategy.ID_TOKEN,
              idToken = token,
          ),
      )
    }
  }

  @Test
  fun `resolveIdentity with id token should keep provider specific missing subject failure`() {
    val issuer = "https://kauth.kakao.com"
    val audience = "kakao-client"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kakao-key",
            issuer = issuer,
            audience = audience,
            subject = null,
            additionalClaims = mapOf("nonce" to "nonce-3"),
        )

    val provider = createProvider(idTokenParser = parser)

    val exception =
        assertFailsWith<InvalidOauthRequestException> {
          provider.resolveIdentity(
              OauthIdentityRequest(
                  strategy = OauthIdentityStrategy.ID_TOKEN,
                  idToken = token,
                  nonce = "nonce-3",
              ),
          )
        }

    assertEquals("Kakao id token does not include subject.", exception.message)
  }

  @Test
  fun `resolveIdentity with user info api should use oidc endpoint`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "sub": "123",
        "email": "user@example.com",
        "nickname": "Tester",
        "picture": "https://cdn.example.com/profile.png"
      }
      """
            .trimIndent()

    server
        .expect(
            ExpectedCount.once(),
            requestTo(containsString("https://kapi.kakao.com/v1/oidc/userinfo")),
        )
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))

    val provider =
        KakaoOauthProvider(
            client = builder.build(),
            clientId = "kakao-client",
            clientSecret = "secret",
            serverRedirectUri = "https://api.example.com/oauth/kakao/callback",
            idTokenParser =
                JwtTestFixtures.createIdTokenParser(
                    issuer = "https://kauth.kakao.com",
                    allowedAudiences = setOf("kakao-client"),
                    publicKey =
                        JwtTestFixtures.generateRsaKeyPair().public
                            as java.security.interfaces.RSAPublicKey,
                ),
            stateManager = createStateManager(),
        )

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.USER_INFO_API,
                accessToken = "access-token",
            ),
        )

    assertEquals("123", identity.userId)
    assertEquals("user@example.com", identity.email)
    assertEquals("Tester", identity.displayName)
    assertEquals("https://cdn.example.com/profile.png", identity.pictureUrl)
    server.verify()
  }

  @Test
  fun `resolveIdentity should support id-only payload mode`() {
    val builder = RestClient.builder()
    val server = MockRestServiceServer.bindTo(builder).build()
    val responseBody =
        """
      {
        "sub": "123",
        "email": "user@example.com"
      }
      """
            .trimIndent()

    server
        .expect(
            ExpectedCount.once(),
            requestTo(containsString("https://kapi.kakao.com/v1/oidc/userinfo")),
        )
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

    assertEquals("123", identity.userId)
    assertNull(identity.email)
    assertEquals(mapOf("sub" to "123"), identity.claims)
    server.verify()
  }

  @Test
  fun `unsupported revoke should include capability`() {
    val provider = createProvider()

    val exception =
        assertFailsWith<UnsupportedOauthOperationException> {
          provider.revokeToken(OauthTokenRevokeRequest(accessToken = "token"))
        }

    assertEquals(OauthProviderCapability.REVOKE_TOKEN, exception.capability)
  }

  @Test
  fun `auto strategy should fail without usable token`() {
    val provider = createProvider()

    assertFailsWith<InvalidOauthRequestException> {
      provider.resolveIdentity(OauthIdentityRequest(strategy = OauthIdentityStrategy.AUTO))
    }
  }

  private fun createProvider(
      restClient: RestClient = RestClient.create(),
      stateManager: OauthStateManager = createStateManager(),
      idTokenParser: com.infosung.atomic.oauth.idtoken.IdTokenParser =
          JwtTestFixtures.createIdTokenParser(
              issuer = "https://kauth.kakao.com",
              allowedAudiences = setOf("kakao-client"),
              publicKey =
                  JwtTestFixtures.generateRsaKeyPair().public
                      as java.security.interfaces.RSAPublicKey,
          ),
  ): KakaoOauthProvider {
    return KakaoOauthProvider(
        client = restClient,
        clientId = "kakao-client",
        clientSecret = "secret",
        serverRedirectUri = "https://api.example.com/oauth/kakao/callback",
        idTokenParser = idTokenParser,
        stateManager = stateManager,
    )
  }

  private fun createStateManager(): OauthStateManager {
    return OauthStateManager(
        signingSecret = "k".repeat(32),
        issuer = "kakao-state-test",
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
}
