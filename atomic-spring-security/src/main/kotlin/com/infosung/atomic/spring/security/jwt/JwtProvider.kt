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
    private val accessKeys: JwtKeyRing,
    private val refreshKeys: JwtKeyRing,
    algorithm: String = "HmacSHA512",
    serviceName: String = "InfosungAtomic",
    private val accessExpiredSecond: Long,
    private val refreshExpiredSecond: Long,
    private val timeProvider: TimeProvider = TimeProvider(),
) {
  constructor(
      accessKey: String,
      refreshKey: String,
      algorithm: String = "HmacSHA512",
      serviceName: String = "InfosungAtomic",
      accessExpiredSecond: Long,
      refreshExpiredSecond: Long,
      timeProvider: TimeProvider = TimeProvider(),
      accessKeyId: String = DEFAULT_ACCESS_KEY_ID,
      refreshKeyId: String = DEFAULT_REFRESH_KEY_ID,
      previousAccessKeys: Map<String, String> = emptyMap(),
      previousRefreshKeys: Map<String, String> = emptyMap(),
  ) : this(
      accessKeys =
          JwtKeyRing(
              active = JwtSigningKey(accessKeyId, accessKey),
              previous = previousAccessKeys.map { (keyId, secret) -> JwtSigningKey(keyId, secret) },
          ),
      refreshKeys =
          JwtKeyRing(
              active = JwtSigningKey(refreshKeyId, refreshKey),
              previous =
                  previousRefreshKeys.map { (keyId, secret) -> JwtSigningKey(keyId, secret) },
          ),
      algorithm = algorithm,
      serviceName = serviceName,
      accessExpiredSecond = accessExpiredSecond,
      refreshExpiredSecond = refreshExpiredSecond,
      timeProvider = timeProvider,
  )

  private data class JwtAlgorithmSpec(
      val jcaName: String,
      val jwsAlgorithm: JWSAlgorithm,
      val macAlgorithm: org.springframework.security.oauth2.jose.jws.MacAlgorithm,
  )

  private data class JwtKeyMaterial(
      val keyId: String,
      val secretKey: SecretKey,
  )

  private data class JwtDecoderSet(
      val byKeyId: Map<String, JwtDecoder>,
      val fallback: List<JwtDecoder>,
  )

  private val serviceName: String = serviceName.ifBlank { "InfosungAtomic" }
  private val issuer = this.serviceName
  private val algorithmSpec = resolveAlgorithmSpec(algorithm)
  private val accessKeyMaterials = buildKeyMaterials(accessKeys)
  private val refreshKeyMaterials = buildKeyMaterials(refreshKeys)
  private val accessDecoderSet = buildDecoderSet(accessKeyMaterials)
  private val refreshDecoderSet = buildDecoderSet(refreshKeyMaterials)
  private val relaxedAccessDecoderSet = buildDecoderSet(accessKeyMaterials)

  private val log = LoggerFactory.getLogger(JwtProvider::class.java)

  init {
    log.info(
        "JwtProvider initialized: issuer={}, accessExpireSeconds={}, refreshExpireSeconds={}",
        issuer,
        accessExpiredSecond,
        refreshExpiredSecond,
    )
    log.debug(
        "JwtProvider key rotation configured: accessActiveKeyId={}, accessPreviousKeys={}, refreshActiveKeyId={}, refreshPreviousKeys={}",
        accessKeys.active.keyId,
        accessKeys.previous.size,
        refreshKeys.active.keyId,
        refreshKeys.previous.size,
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
        accessToken =
            createJsonWebToken(
                id = id,
                subject = subject,
                keyMaterial = accessKeyMaterials.first(),
                issuedAtInstant = now,
                expiredInstant = accessExpiredInstant,
            ),
        refreshToken =
            createJsonWebToken(
                id = id,
                subject = subject,
                keyMaterial = refreshKeyMaterials.first(),
                issuedAtInstant = now,
                expiredInstant = refreshExpiredInstant,
            ),
        accessExpiredTime = accessExpiredInstant.toEpochMilli(),
        refreshExpiredTime = refreshExpiredInstant.toEpochMilli(),
    )
  }

  /** Decodes and validates access-token claims. */
  fun getAccessClaims(jwt: String): Jwt =
      getUserClaims(jwt, accessDecoderSet, tokenType = "access", validateTimestamp = true)

  /** Decodes and validates refresh-token claims. */
  fun getRefreshClaims(jwt: String): Jwt =
      getUserClaims(jwt, refreshDecoderSet, tokenType = "refresh", validateTimestamp = true)

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
            decoderSet = relaxedAccessDecoderSet,
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
      keyMaterial: JwtKeyMaterial,
      issuedAtInstant: Instant,
      expiredInstant: Instant,
  ): String {
    log.trace(
        "Building signed JWT: userId={}, subject={}, keyId={}, algorithm={}, issuedAt={}, expireAt={}",
        id,
        subject,
        keyMaterial.keyId,
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
    val header =
        JWSHeader.Builder(algorithmSpec.jwsAlgorithm)
            .type(JOSEObjectType.JWT)
            .keyID(keyMaterial.keyId)
            .build()

    return try {
      SignedJWT(header, claimsSet)
          .apply { sign(MACSigner(keyMaterial.secretKey.encoded)) }
          .serialize()
    } catch (e: JOSEException) {
      throw HttpInvalidTokenException("Failed to sign token.", e)
    }
  }

  private fun getUserClaims(
      jwt: String,
      decoderSet: JwtDecoderSet,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    val claims = parseToken(jwt, decoderSet, tokenType, validateTimestamp)

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
      decoderSet: JwtDecoderSet,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    val keyId = resolveKeyId(jwt)
    if (!keyId.isNullOrBlank()) {
      val decoder =
          decoderSet.byKeyId[keyId]
              ?: throw HttpInvalidTokenException("Token key id is not recognized.")
      return decodeWithDecoder(
          jwt = jwt,
          decoder = decoder,
          tokenType = tokenType,
          validateTimestamp = validateTimestamp,
          selectedKeyId = keyId,
      )
    }

    var lastFailure: Exception? = null
    for ((index, decoder) in decoderSet.fallback.withIndex()) {
      try {
        log.trace(
            "Parsing legacy token without kid: type={}, decoderIndex={}, {}",
            tokenType,
            index,
            tokenSummary(jwt),
        )
        val claims = decoder.decode(jwt)
        if (validateTimestamp) {
          validateTimestamps(claims)
        }
        return claims
      } catch (e: HttpStatusException) {
        throw e
      } catch (e: HttpInvalidTokenException) {
        throw e
      } catch (e: Exception) {
        lastFailure = e
      }
    }

    log.warn("Token parsing failed: type={}, {}", tokenType, tokenSummary(jwt), lastFailure)
    throw HttpInvalidTokenException("Token parsing failed.", lastFailure)
  }

  private fun decodeWithDecoder(
      jwt: String,
      decoder: JwtDecoder,
      tokenType: String,
      validateTimestamp: Boolean,
      selectedKeyId: String,
  ): Jwt {
    return try {
      log.trace(
          "Parsing signed token: type={}, keyId={}, {}",
          tokenType,
          selectedKeyId,
          tokenSummary(jwt))
      decoder.decode(jwt).also {
        if (validateTimestamp) {
          validateTimestamps(it)
        }
      }
    } catch (e: HttpStatusException) {
      throw e
    } catch (e: HttpInvalidTokenException) {
      throw e
    } catch (e: Exception) {
      if (isExpiredTokenError(e)) {
        log.debug("Token is expired: {}", tokenSummary(jwt))
        throw HttpInvalidTokenException("Token is expired.", e)
      }
      log.warn(
          "Token parsing failed: type={}, keyId={}, {}",
          tokenType,
          selectedKeyId,
          tokenSummary(jwt),
          e)
      throw HttpInvalidTokenException("Token parsing failed.", e)
    }
  }

  private fun buildKeyMaterials(keyRing: JwtKeyRing): List<JwtKeyMaterial> =
      (listOf(keyRing.active) + keyRing.previous).map { key ->
        JwtKeyMaterial(keyId = key.keyId, secretKey = toSecretKey(key.secret))
      }

  private fun buildDecoderSet(materials: List<JwtKeyMaterial>): JwtDecoderSet {
    val decoders = materials.map { it.keyId to createDecoder(it.secretKey) }
    return JwtDecoderSet(
        byKeyId = linkedMapOf(*decoders.toTypedArray()), fallback = decoders.map { it.second })
  }

  private fun toSecretKey(value: String): SecretKey {
    val encoded = Base64.getEncoder().encode(value.toByteArray(StandardCharsets.UTF_8))
    return SecretKeySpec(encoded, algorithmSpec.jcaName)
  }

  private fun resolveKeyId(jwt: String): String? {
    return try {
      SignedJWT.parse(jwt).header.keyID
    } catch (_: Exception) {
      null
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
    return "len=${jwt.length}"
  }

  companion object {
    const val DEFAULT_ACCESS_KEY_ID = "access-current"
    const val DEFAULT_REFRESH_KEY_ID = "refresh-current"
  }
}
