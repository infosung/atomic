package com.infosung.atomic.oauth.provider.apple

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import com.infosung.atomic.oauth.api.OauthIdentityPayloadMode
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
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

class AppleOauthProviderTest {
  @Test
  fun `capability matrix should match apple behavior`() {
    val provider = createProvider()
    assertTrue(provider.supports(OauthProviderCapability.AUTHORIZATION_URL))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE))
    assertTrue(provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE))
    assertTrue(!provider.supports(OauthProviderCapability.AUTHORIZATION_PKCE_S256))
    assertTrue(!provider.supports(OauthProviderCapability.AUTHORIZATION_PKCE_PLAIN))
    assertTrue(!provider.supports(OauthProviderCapability.EXCHANGE_TOKEN))
    assertTrue(!provider.supports(OauthProviderCapability.REFRESH_TOKEN))
    assertTrue(!provider.supports(OauthProviderCapability.REVOKE_TOKEN))
    assertTrue(!provider.supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO))
  }

  @Test
  fun `buildAuthorizationUrl should include required parameters`() {
    val stateManager = createStateManager()
    val provider = createProvider(stateManager = stateManager)
    val clientRedirectUri = "myapp://oauth/apple/callback"

    val url =
        provider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                redirectUri = clientRedirectUri,
                scopes = setOf("email", "name"),
                nonce = "nonce-value",
            ),
        )

    assertTrue(url.startsWith("https://appleid.apple.com/auth/authorize?"))
    assertTrue(url.contains("client_id=apple-client"))
    assertTrue(url.contains("response_type=code+id_token"))
    assertTrue(url.contains("response_mode=form_post"))
    assertTrue(url.contains("scope=email+name") || url.contains("scope=name+email"))
    assertTrue(url.contains("state="))
    assertTrue(
        url.contains("redirect_uri=https%3A%2F%2Fapi.example.com%2Foauth%2Fapple%2Fcallback"))
    assertTrue(url.contains("nonce=nonce-value"))

    val state = extractQueryParam(url, "state")
    val verified =
        stateManager.verifyState(
            signedState = state,
            expectedProvider = OauthProviderName.APPLE,
            expectedRedirectUri = clientRedirectUri,
            expectedNonce = "nonce-value",
        )
    assertEquals("APPLE", verified.claims["provider"])
    assertEquals("nonce-value", verified.claims["nonce"])
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

    assertTrue(url.contains("client_id=apple-client"))
    assertTrue(!url.contains("client_id=evil-client"))
    assertTrue(url.contains("response_type=code+id_token"))
    assertTrue(!url.contains("response_type=token"))
    assertTrue(url.contains("foo=bar"))
  }

  @Test
  fun `buildAuthorizationUrl should reject pkce because apple runtime capability does not expose it`() {
    val provider = createProvider()

    val exception =
        assertFailsWith<UnsupportedOauthOperationException> {
          provider.buildAuthorizationUrl(
              OauthAuthorizationRequest(
                  codeChallenge = "challenge-value",
                  codeChallengeMethod = OauthCodeChallengeMethod.S256,
              ),
          )
        }

    assertEquals(OauthProviderCapability.AUTHORIZATION_PKCE_S256, exception.capability)
  }

  @Test
  fun `resolveIdentity should parse id token claims`() {
    val issuer = "https://appleid.apple.com"
    val audience = "apple-client"
    val kid = "apple-key-1"
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
            subject = "apple-user",
            email = "apple@example.com",
            additionalClaims = mapOf("nonce" to "nonce-1"),
        )

    val provider =
        AppleOauthProvider(
            clientId = audience,
            serverRedirectUri = "https://api.example.com/oauth/apple/callback",
            idTokenParser = parser,
            stateManager = createStateManager(),
        )

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.ID_TOKEN,
                idToken = token,
                nonce = "nonce-1",
                scopes = setOf("email"),
            ),
        )

    assertEquals("apple-user", identity.userId)
    assertEquals("apple@example.com", identity.email)
    assertEquals(setOf("email"), identity.scopes)
  }

  @Test
  fun `resolveIdentity should require nonce by default`() {
    val issuer = "https://appleid.apple.com"
    val audience = "apple-client"
    val kid = "apple-key-1"
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
            subject = "apple-user",
            email = "apple@example.com",
        )

    val provider =
        AppleOauthProvider(
            clientId = audience,
            serverRedirectUri = "https://api.example.com/oauth/apple/callback",
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
  fun `resolveIdentity should keep provider specific missing subject failure`() {
    val issuer = "https://appleid.apple.com"
    val audience = "apple-client"
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
            kid = "apple-key-1",
            issuer = issuer,
            audience = audience,
            subject = null,
            additionalClaims = mapOf("nonce" to "nonce-3"),
        )

    val provider =
        AppleOauthProvider(
            clientId = audience,
            serverRedirectUri = "https://api.example.com/oauth/apple/callback",
            idTokenParser = parser,
            stateManager = createStateManager(),
        )

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

    assertEquals("Apple id token does not include subject.", exception.message)
  }

  @Test
  fun `resolveIdentity should support id-only payload mode`() {
    val issuer = "https://appleid.apple.com"
    val audience = "apple-client"
    val kid = "apple-key-1"
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
            subject = "apple-user",
            email = "apple@example.com",
            additionalClaims = mapOf("nonce" to "nonce-2"),
        )

    val provider =
        AppleOauthProvider(
            clientId = audience,
            serverRedirectUri = "https://api.example.com/oauth/apple/callback",
            idTokenParser = parser,
            stateManager = createStateManager(),
        )

    val identity =
        provider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.ID_TOKEN,
                idToken = token,
                nonce = "nonce-2",
                payloadMode = OauthIdentityPayloadMode.ID_ONLY,
            ),
        )

    assertEquals("apple-user", identity.userId)
    assertNull(identity.email)
    assertEquals(mapOf("sub" to "apple-user"), identity.claims)
  }

  @Test
  fun `resolveIdentity auto strategy should fail without id token`() {
    val provider = createProvider()

    assertFailsWith<InvalidOauthRequestException> {
      provider.resolveIdentity(OauthIdentityRequest(strategy = OauthIdentityStrategy.AUTO))
    }
  }

  @Test
  fun `unsupported operations should include capability in exception`() {
    val provider = createProvider()

    val exception =
        assertFailsWith<UnsupportedOauthOperationException> {
          provider.exchangeCode(
              OauthTokenExchangeRequest(
                  code = "code",
                  state = "state",
              ),
          )
        }

    assertEquals(OauthProviderCapability.EXCHANGE_TOKEN, exception.capability)
  }

  private fun createProvider(
      stateManager: OauthStateManager = createStateManager(),
  ): AppleOauthProvider {
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = "https://appleid.apple.com",
            allowedAudiences = setOf("apple-client"),
            publicKey =
                JwtTestFixtures.generateRsaKeyPair().public
                    as java.security.interfaces.RSAPublicKey,
        )

    return AppleOauthProvider(
        clientId = "apple-client",
        serverRedirectUri = "https://api.example.com/oauth/apple/callback",
        idTokenParser = parser,
        stateManager = stateManager,
    )
  }

  private fun createStateManager(): OauthStateManager {
    return OauthStateManager(
        signingSecret = "a".repeat(32),
        issuer = "apple-state-test",
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
