package com.infosung.atomic.oauth.idtoken

import com.infosung.atomic.oauth.exception.HttpJwtVerifyException
import com.infosung.atomic.oauth.testsupport.JwtTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IdTokenParserTest {
  @Test
  fun `verifyIdToken should validate token with configured allowed audience`() {
    val issuer = "https://issuer.example.com"
    val audience = "client-id"
    val kid = "kid-1"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audience,
            email = "user@example.com",
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    val claims = parser.verifyIdToken(jwt = token)

    assertEquals("user-1", claims.subject)
    assertEquals("user@example.com", claims.getClaim<String>("email"))
  }

  @Test
  fun `verifyIdToken should fail when token audience is outside allowed audiences`() {
    val issuer = "https://issuer.example.com"
    val allowedAudience = "client-id"
    val tokenAudience = "other-client"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kid-1",
            issuer = issuer,
            audience = tokenAudience,
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(allowedAudience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdToken(jwt = token) }
  }

  @Test
  fun `verifyIdToken should enforce required audience from allowed audience set`() {
    val issuer = "https://issuer.example.com"
    val audienceWeb = "client-web"
    val audienceIos = "client-ios"
    val kid = "kid-1"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audienceWeb,
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audienceWeb, audienceIos),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    val verified = parser.verifyIdToken(jwt = token, requiredAudience = audienceWeb)
    assertEquals("user-1", verified.subject)

    assertFailsWith<HttpJwtVerifyException> {
      parser.verifyIdToken(jwt = token, requiredAudience = "client-android")
    }
    assertFailsWith<HttpJwtVerifyException> {
      parser.verifyIdToken(jwt = token, requiredAudience = audienceIos)
    }
  }

  @Test
  fun `verifyIdToken should validate nonce when expected nonce is provided`() {
    val issuer = "https://issuer.example.com"
    val audience = "client-id"
    val kid = "kid-1"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audience,
            additionalClaims = mapOf("nonce" to "nonce-1"),
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    val verified = parser.verifyIdToken(jwt = token, expectedNonce = "nonce-1")
    assertEquals("user-1", verified.subject)

    assertFailsWith<HttpJwtVerifyException> {
      parser.verifyIdToken(jwt = token, expectedNonce = "nonce-mismatch")
    }
  }

  @Test
  fun `verifyIdTokenClaims should expose typed claims while keeping provider specific claim access`() {
    val issuer = "https://issuer.example.com"
    val audience = "client-id"
    val kid = "kid-1"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = kid,
            issuer = issuer,
            audience = audience,
            subject = "typed-user",
            email = "typed@example.com",
            additionalClaims = mapOf("nonce" to "nonce-2", "nickname" to "typed-nick"),
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    val verified = parser.verifyIdTokenClaims(jwt = token, expectedNonce = "nonce-2")

    assertEquals(issuer, verified.issuer)
    assertEquals("typed-user", verified.subject)
    assertEquals(listOf(audience), verified.audiences)
    assertEquals("nonce-2", verified.nonce)
    assertEquals("typed@example.com", verified.stringClaim("email"))
    assertEquals("typed-nick", verified.stringClaim("nickname"))
    assertNotNull(verified.issuedAt)
    assertNotNull(verified.expiresAt)
    assertTrue(verified.claims.containsKey("email"))
  }

  @Test
  fun `verifyIdTokenClaims should fail on the same invalid audience paths as legacy verification`() {
    val issuer = "https://issuer.example.com"
    val allowedAudience = "client-id"
    val tokenAudience = "other-client"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kid-1",
            issuer = issuer,
            audience = tokenAudience,
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(allowedAudience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdToken(jwt = token) }
    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdTokenClaims(jwt = token) }
  }

  @Test
  fun `verifyIdTokenClaims should fail on the same invalid nonce paths as legacy verification`() {
    val issuer = "https://issuer.example.com"
    val audience = "client-id"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kid-1",
            issuer = issuer,
            audience = audience,
            additionalClaims = mapOf("nonce" to "nonce-1"),
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    assertFailsWith<HttpJwtVerifyException> {
      parser.verifyIdToken(jwt = token, expectedNonce = "nonce-mismatch")
    }
    assertFailsWith<HttpJwtVerifyException> {
      parser.verifyIdTokenClaims(jwt = token, expectedNonce = "nonce-mismatch")
    }
  }

  @Test
  fun `verifyIdTokenClaims should fail on the same decode paths as legacy verification`() {
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = "https://issuer.example.com",
            allowedAudiences = setOf("client-id"),
            publicKey =
                JwtTestFixtures.generateRsaKeyPair().public
                    as java.security.interfaces.RSAPublicKey,
        )

    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdToken(jwt = "not-a-jwt") }
    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdTokenClaims(jwt = "not-a-jwt") }
  }

  @Test
  fun `verifyIdTokenClaims should fail on the same issuer validation paths as legacy verification`() {
    val tokenIssuer = "https://issuer-a.example.com"
    val parserIssuer = "https://issuer-b.example.com"
    val audience = "client-id"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kid-1",
            issuer = tokenIssuer,
            audience = audience,
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = parserIssuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdToken(jwt = token) }
    assertFailsWith<HttpJwtVerifyException> { parser.verifyIdTokenClaims(jwt = token) }
  }

  @Test
  fun `verifyIdTokenClaims should preserve missing subject for provider level validation`() {
    val issuer = "https://issuer.example.com"
    val audience = "client-id"
    val keyPair = JwtTestFixtures.generateRsaKeyPair()
    val token =
        JwtTestFixtures.createSignedJwt(
            privateKey = keyPair.private,
            kid = "kid-1",
            issuer = issuer,
            audience = audience,
            subject = null,
            additionalClaims = mapOf("nonce" to "nonce-3"),
        )
    val parser =
        JwtTestFixtures.createIdTokenParser(
            issuer = issuer,
            allowedAudiences = setOf(audience),
            publicKey = keyPair.public as java.security.interfaces.RSAPublicKey,
        )

    val verified = parser.verifyIdTokenClaims(jwt = token, expectedNonce = "nonce-3")

    assertEquals(null, verified.subject)
    assertEquals("nonce-3", verified.nonce)
  }
}
