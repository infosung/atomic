package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.exception.HttpTokenNotExpiredException
import com.infosung.atomic.contract.time.TimeProvider
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * HMAC-based JWT issuer and validator.
 *
 * Generates access/refresh token pair and validates issuer/service claims.
 */
class JwtProvider(
    accessKey: String,
    refreshKey: String,
    algorithm: String = "HmacSHA512",
    serviceName: String = "InfosungAtomic",
    private val accessExpiredSecond: Long,
    private val refreshExpiredSecond: Long,
    private val timeProvider: TimeProvider = TimeProvider(),
) {
  private data class JwtAlgorithmSpec(
      val jcaName: String,
      val jwsAlgorithm: JWSAlgorithm,
      val macAlgorithm: org.springframework.security.oauth2.jose.jws.MacAlgorithm,
  )

  private val serviceName: String = serviceName.ifBlank { "InfosungAtomic" }
  private val issuer = this.serviceName
  private val algorithmSpec = resolveAlgorithmSpec(algorithm)

  private val accessKeyBytes =
      Base64.getEncoder().encode(accessKey.toByteArray(StandardCharsets.UTF_8))
  private val refreshKeyBytes =
      Base64.getEncoder().encode(refreshKey.toByteArray(StandardCharsets.UTF_8))

  private val userAccessKey: SecretKey = SecretKeySpec(accessKeyBytes, algorithmSpec.jcaName)
  private val userRefreshKey: SecretKey = SecretKeySpec(refreshKeyBytes, algorithmSpec.jcaName)

  private val strictAccessDecoder: JwtDecoder = createDecoder(userAccessKey)
  private val strictRefreshDecoder: JwtDecoder = createDecoder(userRefreshKey)
  private val relaxedAccessDecoder: JwtDecoder = createDecoder(userAccessKey)

  private val log = LoggerFactory.getLogger(JwtProvider::class.java)

  init {
    log.info(
        "JwtProvider initialized: issuer={}, accessExpireSeconds={}, refreshExpireSeconds={}",
        issuer,
        accessExpiredSecond,
        refreshExpiredSecond,
    )
  }

  /** Creates access/refresh token pair for [id]/[subject]. */
  fun createJwtDto(
      id: String,
      subject: String = "APPLE",
  ): JwtDto {
    val now = timeProvider.nowInstant()
    val accessExpiredInstant = now.plusSeconds(accessExpiredSecond)
    val refreshExpiredInstant = now.plusSeconds(refreshExpiredSecond)
    log.debug(
        "Creating JWT pair: userId={}, subject={}, issuedAt={}, accessExpireAt={}, refreshExpireAt={}",
        id,
        subject,
        now,
        accessExpiredInstant,
        refreshExpiredInstant,
    )

    return JwtDto(
        id = id,
        accessToken = createJsonWebToken(id, subject, accessKeyBytes, now, accessExpiredInstant),
        refreshToken = createJsonWebToken(id, subject, refreshKeyBytes, now, refreshExpiredInstant),
        accessExpiredTime = accessExpiredInstant.toEpochMilli(),
        refreshExpiredTime = refreshExpiredInstant.toEpochMilli(),
    )
  }

  /**
   * Decodes and validates access-token claims.
   *
   * @throws HttpInvalidTokenException If token is malformed/expired/invalid.
   */
  fun getAccessClaims(jwt: String): Jwt =
      getUserClaims(jwt, strictAccessDecoder, tokenType = "access", validateTimestamp = true)

  /**
   * Decodes and validates refresh-token claims.
   *
   * @throws HttpInvalidTokenException If token is malformed/expired/invalid.
   */
  fun getRefreshClaims(jwt: String): Jwt =
      getUserClaims(jwt, strictRefreshDecoder, tokenType = "refresh", validateTimestamp = true)

  /**
   * Decodes expired access-token claims without timestamp validator.
   *
   * @throws HttpTokenNotExpiredException If token is still valid.
   * @throws HttpInvalidTokenException If token is malformed/invalid.
   */
  fun getExpiredClaims(jwt: String): Jwt {
    val claims =
        parseToken(
            jwt = jwt,
            decoder = relaxedAccessDecoder,
            tokenType = "access",
            validateTimestamp = false,
        )
    val expiresAt =
        claims.expiresAt ?: throw HttpInvalidTokenException("Token does not include expiration.")
    if (timeProvider.nowInstant().isBefore(expiresAt)) {
      log.warn("Token is not expired yet: {}", tokenSummary(jwt))
      throw HttpTokenNotExpiredException("Token is not expired yet.")
    }
    return claims
  }

  private fun createJsonWebToken(
      id: String,
      subject: String,
      keyBytes: ByteArray,
      issuedAtInstant: Instant,
      expiredInstant: Instant,
  ): String {
    log.trace(
        "Building signed JWT: userId={}, subject={}, algorithm={}, issuedAt={}, expireAt={}",
        id,
        subject,
        algorithmSpec.jwsAlgorithm.name,
        issuedAtInstant,
        expiredInstant,
    )

    val claimsSet =
        JWTClaimsSet.Builder()
            .jwtID(id)
            .issuer(issuer)
            .subject(subject)
            .issueTime(Date.from(issuedAtInstant))
            .expirationTime(Date.from(expiredInstant))
            .claim("service_name", serviceName)
            .build()
    val header = JWSHeader.Builder(algorithmSpec.jwsAlgorithm).type(JOSEObjectType.JWT).build()

    return try {
      SignedJWT(header, claimsSet).apply { sign(MACSigner(keyBytes)) }.serialize()
    } catch (e: JOSEException) {
      throw HttpInvalidTokenException("Failed to sign token.", e)
    }
  }

  private fun getUserClaims(
      jwt: String,
      decoder: JwtDecoder,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    val claims = parseToken(jwt, decoder, tokenType, validateTimestamp)

    val tokenIssuer = claims.claims["iss"]?.toString()
    val tokenServiceName = claims.claims["service_name"]?.toString()
    if (tokenIssuer != issuer || tokenServiceName != serviceName) {
      log.warn(
          "Invalid token issuer/service_name: type={}, issuer={}, service_name={}, expectedIssuer={}, expectedService={}",
          tokenType,
          tokenIssuer,
          tokenServiceName,
          issuer,
          serviceName,
      )
      throw HttpInvalidTokenException("Invalid token.")
    }

    if (claims.id.isNullOrBlank() || claims.subject.isNullOrBlank()) {
      throw HttpInvalidTokenException("Token does not include required id/subject claims.")
    }

    log.trace(
        "Token validated: type={}, id={}, subject={}, issuer={}",
        tokenType,
        claims.id,
        claims.subject,
        tokenIssuer,
    )
    return claims
  }

  private fun parseToken(
      jwt: String,
      decoder: JwtDecoder,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    return try {
      log.trace("Parsing signed token: {}", tokenSummary(jwt))
      decoder.decode(jwt).also {
        if (validateTimestamp) {
          validateTimestamps(it)
        }
      }
    } catch (e: HttpStatusException) {
      throw e
    } catch (e: Exception) {
      if (isExpiredTokenError(e)) {
        log.debug("Token is expired: {}", tokenSummary(jwt))
        throw HttpInvalidTokenException("Token is expired.", e)
      }
      log.warn("Token parsing failed: type={}, {}", tokenType, tokenSummary(jwt), e)
      throw HttpInvalidTokenException("Token parsing failed.", e)
    }
  }

  private fun createDecoder(key: SecretKey): JwtDecoder {
    val decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(algorithmSpec.macAlgorithm).build()
    decoder.setJwtValidator(DelegatingOAuth2TokenValidator(issuerValidator()))
    return decoder
  }

  private fun issuerValidator(): OAuth2TokenValidator<Jwt> {
    return OAuth2TokenValidator { token ->
      val tokenIssuer = token.claims["iss"]?.toString()
      if (tokenIssuer == issuer) {
        OAuth2TokenValidatorResult.success()
      } else {
        OAuth2TokenValidatorResult.failure(
            OAuth2Error(
                "invalid_token",
                "Token issuer does not match configured issuer.",
                null,
            ),
        )
      }
    }
  }

  private fun resolveAlgorithmSpec(algorithm: String): JwtAlgorithmSpec {
    return when (algorithm.uppercase()) {
      "HMACSHA256" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA256",
              jwsAlgorithm = JWSAlgorithm.HS256,
              macAlgorithm = org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256,
          )
      "HMACSHA384" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA384",
              jwsAlgorithm = JWSAlgorithm.HS384,
              macAlgorithm = org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS384,
          )
      "HMACSHA512" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA512",
              jwsAlgorithm = JWSAlgorithm.HS512,
              macAlgorithm = org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS512,
          )
      else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
    }
  }

  private fun isExpiredTokenError(e: Throwable): Boolean {
    val message = e.message?.lowercase() ?: return false
    return message.contains("expired") || message.contains("jwt expired")
  }

  private fun validateTimestamps(jwt: Jwt) {
    val now = timeProvider.nowInstant()
    val expiresAt =
        jwt.expiresAt ?: throw HttpInvalidTokenException("Token does not include expiration.")
    if (!expiresAt.isAfter(now)) {
      throw HttpInvalidTokenException("Token is expired.")
    }

    val notBefore = jwt.notBefore
    if (notBefore != null && now.isBefore(notBefore)) {
      throw HttpInvalidTokenException("Token is not valid yet.")
    }
  }

  private fun tokenSummary(jwt: String): String {
    if (jwt.isBlank()) return "blank"
    val suffix = jwt.takeLast(8)
    return "len=${jwt.length},suffix=$suffix"
  }
}
