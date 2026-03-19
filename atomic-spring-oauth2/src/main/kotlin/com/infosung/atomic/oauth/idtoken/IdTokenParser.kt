package com.infosung.atomic.oauth.idtoken

import com.infosung.atomic.oauth.exception.HttpJwtVerifyException
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/** Generic OIDC id-token verifier using JWK-backed [JwtDecoder]. */
class IdTokenParser(
    private val iss: String,
    private val allowedAudiences: Set<String>,
    jwkSetUri: String? = null,
    jwtDecoder: JwtDecoder? = null,
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val decoder: JwtDecoder =
      jwtDecoder
          ?: run {
            val uri =
                jwkSetUri?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException(
                        "jwkSetUri is required when jwtDecoder is not provided.",
                    )
            NimbusJwtDecoder.withJwkSetUri(uri).build()
          }
  private val tokenValidator: OAuth2TokenValidator<Jwt>

  init {
    require(allowedAudiences.isNotEmpty()) { "allowedAudiences must not be empty." }
    tokenValidator =
        DelegatingOAuth2TokenValidator(
            JwtValidators.createDefaultWithIssuer(iss),
            allowedAudienceValidator(),
        )
  }

  /**
   * Verifies id token signature, issuer, audience, and optional nonce.
   *
   * @throws HttpJwtVerifyException If verification fails.
   */
  fun verifyIdToken(
      jwt: String,
      requiredAudience: String? = null,
      expectedNonce: String? = null,
  ): Jwt {
    return decodeAndValidateIdToken(
        jwt = jwt,
        requiredAudience = requiredAudience,
        expectedNonce = expectedNonce,
    )
  }

  /**
   * Verifies id token and returns a transport-agnostic typed claims model.
   *
   * @throws HttpJwtVerifyException If verification fails.
   */
  fun verifyIdTokenClaims(
      jwt: String,
      requiredAudience: String? = null,
      expectedNonce: String? = null,
  ): OauthIdTokenClaims {
    val verifiedJwt =
        decodeAndValidateIdToken(
            jwt = jwt,
            requiredAudience = requiredAudience,
            expectedNonce = expectedNonce,
        )
    val claims =
        OauthIdTokenClaims(
            issuer =
                verifiedJwt.issuer?.toString()
                    ?: throw HttpJwtVerifyException("Id token does not include issuer."),
            subject = verifiedJwt.subject,
            audiences = verifiedJwt.audience,
            issuedAt = verifiedJwt.issuedAt,
            expiresAt = verifiedJwt.expiresAt,
            nonce = verifiedJwt.claims["nonce"]?.toString(),
            claims = verifiedJwt.claims.toMap(),
        )
    log.debug(
        "Mapped verified id token to typed claims: subject={}, audienceCount={}, hasNonce={}.",
        claims.subject,
        claims.audiences.size,
        !claims.nonce.isNullOrBlank(),
    )
    return claims
  }

  private fun decodeAndValidateIdToken(
      jwt: String,
      requiredAudience: String? = null,
      expectedNonce: String? = null,
  ): Jwt {
    log.debug(
        "Verifying id token with issuer={} and allowedAudienceCount={}.",
        iss,
        allowedAudiences.size,
    )

    val decoded =
        try {
          decoder.decode(jwt)
        } catch (e: Exception) {
          throw HttpJwtVerifyException("Failed to decode or verify id token.", e)
        }

    val validationResult = tokenValidator.validate(decoded)
    if (validationResult.hasErrors()) {
      val descriptions = validationResult.errors.map { it.description ?: it.errorCode }
      throw HttpJwtVerifyException("Id token validation failed: ${descriptions.joinToString("; ")}")
    }

    if (!requiredAudience.isNullOrBlank()) {
      if (!allowedAudiences.contains(requiredAudience)) {
        throw HttpJwtVerifyException("Required audience is not in configured allowed audiences.")
      }
      if (!decoded.audience.contains(requiredAudience)) {
        throw HttpJwtVerifyException("Id token audience does not include required audience.")
      }
    }

    if (!expectedNonce.isNullOrBlank()) {
      val nonceClaim = decoded.claims["nonce"]?.toString()
      if (nonceClaim.isNullOrBlank()) {
        throw HttpJwtVerifyException("Id token does not include nonce claim.")
      }
      if (nonceClaim != expectedNonce) {
        throw HttpJwtVerifyException("Id token nonce does not match expected nonce.")
      }
    }

    log.debug("Id token verification succeeded for subject={}.", decoded.subject)
    return decoded
  }

  private fun allowedAudienceValidator(): OAuth2TokenValidator<Jwt> {
    return OAuth2TokenValidator { token ->
      val audiences = token.audience
      if (audiences.any { allowedAudiences.contains(it) }) {
        OAuth2TokenValidatorResult.success()
      } else {
        OAuth2TokenValidatorResult.failure(
            OAuth2Error(
                "invalid_token",
                "Id token audience does not match configured allowed audiences.",
                null,
            ),
        )
      }
    }
  }
}
