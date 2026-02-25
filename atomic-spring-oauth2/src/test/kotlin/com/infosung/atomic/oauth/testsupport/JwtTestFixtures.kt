package com.infosung.atomic.oauth.testsupport

import com.infosung.atomic.oauth.idtoken.IdTokenParser
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

object JwtTestFixtures {
  fun generateRsaKeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("RSA")
    generator.initialize(2048)
    return generator.generateKeyPair()
  }

  fun createSignedJwt(
      privateKey: PrivateKey,
      kid: String,
      issuer: String,
      audience: String,
      subject: String = "user-1",
      email: String? = null,
      additionalClaims: Map<String, Any?> = emptyMap(),
  ): String {
    val now = Instant.now()
    val claimsBuilder =
        JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(now.minusSeconds(60)))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .audience(audience)

    if (email != null) {
      claimsBuilder.claim("email", email)
    }
    additionalClaims.forEach { (key, value) ->
      if (value != null) {
        claimsBuilder.claim(key, value)
      }
    }

    val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build()
    return SignedJWT(header, claimsBuilder.build())
        .apply { sign(RSASSASigner(privateKey)) }
        .serialize()
  }

  fun createIdTokenParser(
      issuer: String,
      allowedAudiences: Set<String>,
      publicKey: RSAPublicKey,
  ): IdTokenParser {
    return IdTokenParser(
        iss = issuer,
        allowedAudiences = allowedAudiences,
        jwtDecoder = createJwtDecoder(publicKey),
    )
  }

  fun createJwtDecoder(publicKey: RSAPublicKey): JwtDecoder {
    return NimbusJwtDecoder.withPublicKey(publicKey).build()
  }
}
