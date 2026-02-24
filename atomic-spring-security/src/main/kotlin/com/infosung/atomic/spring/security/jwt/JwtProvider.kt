package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.exception.HttpTokenNotExpiredException
import com.infosung.atomic.contract.time.TimeProvider
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwt
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import java.time.Instant
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory

class JwtProvider(
    accessKey: String,
    refreshKey: String,
    algorithm: String = "HmacSHA512",
    serviceName: String = "InfosungAtomic",
    private val accessExpiredSecond: Long,
    private val refreshExpiredSecond: Long,
    private val timeProvider: TimeProvider = TimeProvider(),
) {
  private val serviceName: String = serviceName.ifBlank { "InfosungAtomic" }
  private val userAccessKey: SecretKey
  private val userRefreshKey: SecretKey
  private val issuer = serviceName
  private val log = LoggerFactory.getLogger(JwtProvider::class.java)

  init {
    val accessKeyBytes = Base64.getEncoder().encode(accessKey.toByteArray())
    userAccessKey = SecretKeySpec(accessKeyBytes, algorithm)

    val refreshKeyBytes = Base64.getEncoder().encode(refreshKey.toByteArray())
    userRefreshKey = SecretKeySpec(refreshKeyBytes, algorithm)

    log.info(
        "JwtProvider initialized: issuer={}, accessExpireSeconds={}, refreshExpireSeconds={}",
        issuer,
        accessExpiredSecond,
        refreshExpiredSecond,
    )
  }

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
        accessToken = createJsonWebToken(id, subject, userAccessKey, now, accessExpiredInstant),
        refreshToken = createJsonWebToken(id, subject, userRefreshKey, now, refreshExpiredInstant),
        accessExpiredTime = accessExpiredInstant.toEpochMilli(),
        refreshExpiredTime = refreshExpiredInstant.toEpochMilli(),
    )
  }

  private fun createJsonWebToken(
      id: String,
      subject: String,
      key: SecretKey,
      issuedAtInstant: Instant,
      expiredInstant: Instant,
  ): String {
    val claims = mutableMapOf<String, Any>()
    claims["service_name"] = serviceName
    log.trace(
        "Building signed JWT: userId={}, subject={}, algorithm={}, issuedAt={}, expireAt={}",
        id,
        subject,
        key.algorithm,
        issuedAtInstant,
        expiredInstant,
    )

    return Jwts.builder()
        .header()
        .add(mapOf("typ" to "JWT", "alg" to key.algorithm))
        .and()
        .claims(claims)
        .id(id)
        .issuer(issuer)
        .subject(subject)
        .issuedAt(Date.from(issuedAtInstant))
        .expiration(Date.from(expiredInstant))
        .signWith(key)
        .compact()
  }

  fun getAccessClaims(jwt: String): Claims =
      getUserId(jwt, userAccessKey, "access") ?: throw HttpInvalidTokenException("Invalid token")

  fun getRefreshClaims(jwt: String): Claims =
      getUserId(jwt, userRefreshKey, "refresh") ?: throw HttpInvalidTokenException("Invalid token")

  fun getExpiredClaims(jwt: String): Claims {
    try {
      log.debug("Validating token is expired: {}", tokenSummary(jwt))
      parser(key = userAccessKey).parseSignedClaims(jwt)
      log.warn("Token is not expired yet: {}", tokenSummary(jwt))
      throw HttpTokenNotExpiredException("Token is not expired yet.")
    } catch (e: ExpiredJwtException) {
      log.debug("Expired token parsed successfully: {}", tokenSummary(jwt))
      return e.claims
    } catch (e: HttpStatusException) {
      throw e
    } catch (e: Exception) {
      log.warn("Failed to parse expired token: {}", tokenSummary(jwt), e)
      throw HttpInvalidTokenException("Token verification failed.", e)
    }
  }

  private fun getUserId(
      jwt: String,
      key: SecretKey,
      tokenType: String,
  ): Claims? {
    val claims = parseToken(jwt, key)
    val payload = claims.payload

    if (payload["service_name"] != serviceName || payload.issuer != issuer) {
      log.warn(
          "Invalid token issuer/service_name: type={}, issuer={}, service_name={}, expectedIssuer={}, expectedService={}",
          tokenType,
          payload.issuer,
          payload["service_name"],
          issuer,
          serviceName,
      )
      throw HttpInvalidTokenException("Invalid token.")
    }
    log.trace(
        "Token validated: type={}, id={}, subject={}, issuer={}",
        tokenType,
        payload.id,
        payload.subject,
        payload.issuer,
    )
    return payload
  }

  private fun parseToken(
      jwt: String,
      key: SecretKey,
  ): Jwt<JwsHeader, Claims> {
    try {
      log.trace("Parsing signed token: {}", tokenSummary(jwt))
      return parser(
              key = key,
              requireIssuer = true,
          )
          .parseSignedClaims(jwt)
    } catch (e: ExpiredJwtException) {
      log.debug("Token is expired: {}", tokenSummary(jwt))
      throw HttpInvalidTokenException("Token is expired.", e)
    } catch (e: Exception) {
      log.warn("Token parsing failed: {}", tokenSummary(jwt), e)
      throw HttpInvalidTokenException("Token parsing failed.", e)
    }
  }

  private fun parser(
      key: SecretKey,
      requireIssuer: Boolean = false,
  ): JwtParser {
    var builder = Jwts.parser().verifyWith(key).clock { Date.from(timeProvider.nowInstant()) }

    if (requireIssuer) {
      builder = builder.requireIssuer(issuer)
    }
    return builder.build()
  }

  private fun tokenSummary(jwt: String): String {
    if (jwt.isBlank()) return "blank"
    val suffix = jwt.takeLast(8)
    return "len=${jwt.length},suffix=$suffix"
  }
}
