package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.exception.HttpTokenNotExpiredException
import com.infosung.atomic.contract.time.TimeProvider
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.LinkedHashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt

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
  )

  private data class JwtKeyMaterial(
      val keyId: String,
      val secretKey: SecretKey,
  )

  private data class JwtVerifierSet(
      val byKeyId: Map<String, JwtKeyMaterial>,
      val fallback: List<JwtKeyMaterial>,
  )

  private val serviceName: String = serviceName.ifBlank { "InfosungAtomic" }
  private val issuer = this.serviceName
  private val algorithmSpec = resolveAlgorithmSpec(algorithm)
  private val accessKeyMaterials = buildKeyMaterials(accessKeys)
  private val refreshKeyMaterials = buildKeyMaterials(refreshKeys)
  private val accessVerifierSet = buildVerifierSet(accessKeyMaterials)
  private val refreshVerifierSet = buildVerifierSet(refreshKeyMaterials)
  private val relaxedAccessVerifierSet = buildVerifierSet(accessKeyMaterials)

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
      getUserClaims(jwt, accessVerifierSet, tokenType = "access", validateTimestamp = true)

  /** Decodes and validates refresh-token claims. */
  fun getRefreshClaims(jwt: String): Jwt =
      getUserClaims(jwt, refreshVerifierSet, tokenType = "refresh", validateTimestamp = true)

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
            verifierSet = relaxedAccessVerifierSet,
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
      verifierSet: JwtVerifierSet,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    val claims = parseToken(jwt, verifierSet, tokenType, validateTimestamp)

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
      verifierSet: JwtVerifierSet,
      tokenType: String,
      validateTimestamp: Boolean,
  ): Jwt {
    val signedJwt = parseSignedJwt(jwt)
    validateHeaderAlgorithm(signedJwt, tokenType)
    val keyId = signedJwt.header.keyID
    if (!keyId.isNullOrBlank()) {
      val keyMaterial =
          verifierSet.byKeyId[keyId]
              ?: throw HttpInvalidTokenException("Token key id is not recognized.")
      return verifyAndBuildJwt(
          signedJwt = signedJwt,
          jwt = jwt,
          keyMaterial = keyMaterial,
          tokenType = tokenType,
          validateTimestamp = validateTimestamp,
          selectedKeyId = keyId,
      )
    }

    for ((index, keyMaterial) in verifierSet.fallback.withIndex()) {
      if (verifySignature(signedJwt, keyMaterial)) {
        log.trace(
            "Parsing legacy token without kid: type={}, keyIndex={}, {}",
            tokenType,
            index,
            tokenSummary(jwt),
        )
        return buildJwt(jwt, signedJwt).also {
          if (validateTimestamp) {
            validateTimestamps(it)
          }
        }
      }
    }

    log.warn("Token parsing failed: type={}, {}", tokenType, tokenSummary(jwt))
    throw HttpInvalidTokenException("Token parsing failed.")
  }

  private fun verifyAndBuildJwt(
      signedJwt: SignedJWT,
      jwt: String,
      keyMaterial: JwtKeyMaterial,
      tokenType: String,
      validateTimestamp: Boolean,
      selectedKeyId: String,
  ): Jwt {
    log.trace(
        "Parsing signed token: type={}, keyId={}, {}",
        tokenType,
        selectedKeyId,
        tokenSummary(jwt),
    )
    if (!verifySignature(signedJwt, keyMaterial)) {
      log.warn(
          "Token parsing failed: type={}, keyId={}, {}",
          tokenType,
          selectedKeyId,
          tokenSummary(jwt),
      )
      throw HttpInvalidTokenException("Token parsing failed.")
    }
    return buildJwt(jwt, signedJwt).also {
      if (validateTimestamp) {
        validateTimestamps(it)
      }
    }
  }

  private fun buildKeyMaterials(keyRing: JwtKeyRing): List<JwtKeyMaterial> =
      (listOf(keyRing.active) + keyRing.previous).map { key ->
        JwtKeyMaterial(keyId = key.keyId, secretKey = toSecretKey(key.secret))
      }

  private fun buildVerifierSet(materials: List<JwtKeyMaterial>): JwtVerifierSet =
      JwtVerifierSet(
          byKeyId = linkedMapOf(*materials.map { it.keyId to it }.toTypedArray()),
          fallback = materials,
      )

  private fun toSecretKey(value: String): SecretKey {
    val encoded = Base64.getEncoder().encode(value.toByteArray(StandardCharsets.UTF_8))
    return SecretKeySpec(encoded, algorithmSpec.jcaName)
  }

  private fun parseSignedJwt(jwt: String): SignedJWT {
    return try {
      SignedJWT.parse(jwt)
    } catch (e: Exception) {
      throw HttpInvalidTokenException("Token parsing failed.", e)
    }
  }

  private fun validateHeaderAlgorithm(
      signedJwt: SignedJWT,
      tokenType: String,
  ) {
    if (signedJwt.header.algorithm != algorithmSpec.jwsAlgorithm) {
      log.warn(
          "Token algorithm mismatch: type={}, actual={}, expected={}",
          tokenType,
          signedJwt.header.algorithm.name,
          algorithmSpec.jwsAlgorithm.name,
      )
      throw HttpInvalidTokenException("Token algorithm is not supported.")
    }
  }

  private fun verifySignature(
      signedJwt: SignedJWT,
      keyMaterial: JwtKeyMaterial,
  ): Boolean {
    return try {
      signedJwt.verify(MACVerifier(keyMaterial.secretKey.encoded))
    } catch (_: JOSEException) {
      false
    }
  }

  private fun buildJwt(
      tokenValue: String,
      signedJwt: SignedJWT,
  ): Jwt {
    val claimsSet = signedJwt.jwtClaimsSet
    val headers =
        LinkedHashMap<String, Any>().apply {
          signedJwt.header.toJSONObject().forEach { (key, value) ->
            normalizeValue(value)?.let { put(key, it) }
          }
        }
    val claims =
        LinkedHashMap<String, Any>().apply {
          claimsSet.claims.forEach { (key, value) ->
            normalizeValue(value)?.let { put(key, it) }
          }
        }
    return Jwt(
        tokenValue,
        claimsSet.issueTime?.toInstant(),
        claimsSet.expirationTime?.toInstant(),
        headers,
        claims,
    )
  }

  private fun normalizeValue(value: Any?): Any? {
    return when (value) {
      null -> null
      is Date -> value.toInstant()
      is Map<*, *> ->
          LinkedHashMap<String, Any>().apply {
            value.forEach { (key, nestedValue) ->
              if (key is String) {
                normalizeValue(nestedValue)?.let { put(key, it) }
              }
            }
          }
      is List<*> -> value.mapNotNull(::normalizeValue)
      else -> value
    }
  }

  private fun resolveAlgorithmSpec(algorithm: String): JwtAlgorithmSpec {
    return when (algorithm.uppercase()) {
      "HMACSHA256" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA256",
              jwsAlgorithm = JWSAlgorithm.HS256,
          )
      "HMACSHA384" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA384",
              jwsAlgorithm = JWSAlgorithm.HS384,
          )
      "HMACSHA512" ->
          JwtAlgorithmSpec(
              jcaName = "HmacSHA512",
              jwsAlgorithm = JWSAlgorithm.HS512,
          )
      else -> throw IllegalArgumentException("Unsupported algorithm: $algorithm")
    }
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
