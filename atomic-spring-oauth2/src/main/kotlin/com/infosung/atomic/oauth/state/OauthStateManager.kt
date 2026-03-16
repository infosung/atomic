package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.api.OauthProviderName
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Issues and verifies signed OAuth state tokens.
 *
 * Provides optional one-time replay protection via [OauthStateStore].
 */
class OauthStateManager(
    signingSecret: String,
    private val issuer: String = "atomic-oauth-state",
    private val ttlSeconds: Long = 300,
    private val store: OauthStateStore? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val maxAttributesEntryCount: Int = 10,
    private val maxAttributesBytes: Int = 512,
    private val maxStateTokenLength: Int = 1200,
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val keyBytes = signingSecret.toByteArray(StandardCharsets.UTF_8)

  init {
    require(keyBytes.size >= 32) { "signingSecret must be at least 32 bytes for HS256." }
    require(ttlSeconds > 0) { "ttlSeconds must be greater than zero." }
    require(maxAttributesEntryCount > 0) { "maxAttributesEntryCount must be greater than zero." }
    require(maxAttributesBytes > 0) { "maxAttributesBytes must be greater than zero." }
    require(maxStateTokenLength > 0) { "maxStateTokenLength must be greater than zero." }
  }

  /**
   * Issues signed OAuth state token.
   *
   * @throws InvalidOauthStateException If attributes violate configured limits.
   */
  fun issueState(
      provider: OauthProviderName? = null,
      redirectUri: String? = null,
      nonce: String? = null,
      attributes: Map<String, String> = emptyMap(),
  ): String {
    validateAttributes(attributes)

    val now = Instant.now(clock)
    val expiresAt = now.plusSeconds(ttlSeconds)
    val stateId = newStateId()

    val claimsBuilder =
        JWTClaimsSet.Builder()
            .issuer(issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .jwtID(stateId)

    provider?.let { claimsBuilder.claim("provider", it.name) }
    redirectUri?.let { claimsBuilder.claim("redirect_uri", it) }
    nonce?.let { claimsBuilder.claim("nonce", it) }
    if (attributes.isNotEmpty()) {
      claimsBuilder.claim("attributes", attributes)
    }

    val token = sign(claimsBuilder.build())
    if (token.length > maxStateTokenLength) {
      throw InvalidOauthStateException(
          "State token is too long. Reduce state attributes.",
      )
    }
    store?.save(stateId = stateId, signedState = token, expiresAt = expiresAt)
    log.debug(
        "Issued OAuth state. stateId={}, provider={}, hasStore={}, ttlSeconds={}",
        stateId,
        provider,
        store != null,
        ttlSeconds,
    )
    return token
  }

  /**
   * Verifies signed OAuth state token and expected values.
   *
   * @throws InvalidOauthStateException If signature/issuer/time/provider/redirect/nonce checks
   *   fail.
   */
  fun verifyState(
      signedState: String,
      expectedProvider: OauthProviderName? = null,
      expectedRedirectUri: String? = null,
      expectedNonce: String? = null,
  ): Jwt {
    return resolveState(
        signedState = signedState,
        expectedProvider = expectedProvider,
        expectedRedirectUri = expectedRedirectUri,
        expectedNonce = expectedNonce,
        consumeStore = true,
    )
  }

  /**
   * Reads and verifies signed OAuth state token without consuming store entry.
   *
   * Useful when caller needs state attributes before deciding how to process token exchange.
   *
   * @throws InvalidOauthStateException If signature/issuer/time/provider/redirect/nonce checks
   *   fail.
   */
  fun readState(
      signedState: String,
      expectedProvider: OauthProviderName? = null,
      expectedRedirectUri: String? = null,
      expectedNonce: String? = null,
  ): Jwt {
    return resolveState(
        signedState = signedState,
        expectedProvider = expectedProvider,
        expectedRedirectUri = expectedRedirectUri,
        expectedNonce = expectedNonce,
        consumeStore = false,
    )
  }

  /** Returns whether this state manager is backed by replay-protection storage. */
  fun isReplayProtectionEnabled(): Boolean = store != null

  private fun resolveState(
      signedState: String,
      expectedProvider: OauthProviderName? = null,
      expectedRedirectUri: String? = null,
      expectedNonce: String? = null,
      consumeStore: Boolean,
  ): Jwt {
    val jwt = parseAndVerifySignature(signedState)
    val claims = jwt.jwtClaimsSet
    val now = Instant.now(clock)

    val tokenIssuer =
        claims.issuer ?: throw InvalidOauthStateException("State token does not include issuer.")
    if (tokenIssuer != issuer) {
      throw InvalidOauthStateException("State token issuer is invalid.")
    }

    val stateId = claims.jwtid ?: throw InvalidOauthStateException("State token has no state id.")
    val issuedAt =
        claims.issueTime?.toInstant()
            ?: throw InvalidOauthStateException("State token does not include issuedAt.")
    val expiresAt =
        claims.expirationTime?.toInstant()
            ?: throw InvalidOauthStateException("State token does not include expiration.")
    if (!expiresAt.isAfter(now)) {
      throw InvalidOauthStateException("State token is expired.")
    }

    val provider = parseProvider(claims.getStringClaim("provider"))
    val redirectUri = claims.getStringClaim("redirect_uri")
    val nonce = claims.getStringClaim("nonce")
    val attributes = parseAttributes(claims.getClaim("attributes"))

    expectedProvider?.let { expected ->
      if (provider != expected) {
        throw InvalidOauthStateException("State provider does not match expected provider.")
      }
    }
    expectedRedirectUri?.let { expected ->
      if (redirectUri != expected) {
        throw InvalidOauthStateException("State redirectUri does not match expected redirectUri.")
      }
    }
    expectedNonce?.let { expected ->
      if (nonce != expected) {
        throw InvalidOauthStateException("State nonce does not match expected nonce.")
      }
    }

    if (consumeStore &&
        store != null &&
        !store.consume(stateId = stateId, signedState = signedState)) {
      throw InvalidOauthStateException("State token is already used, expired, or unknown.")
    }

    log.debug(
        "Verified OAuth state. stateId={}, provider={}, usedStore={}, consumed={}",
        stateId,
        provider,
        store != null,
        consumeStore,
    )
    val claimMap =
        linkedMapOf<String, Any>("iss" to tokenIssuer, "iat" to issuedAt, "exp" to expiresAt)
    claimMap["jti"] = stateId
    provider?.let { claimMap["provider"] = it.name }
    redirectUri?.let { claimMap["redirect_uri"] = it }
    nonce?.let { claimMap["nonce"] = it }
    if (attributes.isNotEmpty()) {
      claimMap["attributes"] = attributes
    }
    val headerMap = linkedMapOf<String, Any>("alg" to jwt.header.algorithm.name)
    jwt.header.type?.let { headerMap["typ"] = it.toString() }

    return Jwt(signedState, issuedAt, expiresAt, headerMap, claimMap)
  }

  private fun sign(claimsSet: JWTClaimsSet): String {
    val header = JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build()
    return try {
      SignedJWT(header, claimsSet).apply { sign(MACSigner(keyBytes)) }.serialize()
    } catch (e: JOSEException) {
      throw InvalidOauthStateException("Failed to sign state token.", e)
    }
  }

  private fun parseAndVerifySignature(signedState: String): SignedJWT {
    val jwt =
        try {
          SignedJWT.parse(signedState)
        } catch (e: Exception) {
          throw InvalidOauthStateException("State token format is invalid.", e)
        }
    val verified =
        try {
          jwt.verify(MACVerifier(keyBytes))
        } catch (e: Exception) {
          throw InvalidOauthStateException("Failed to verify state token signature.", e)
        }
    if (!verified) {
      throw InvalidOauthStateException("State token signature is invalid.")
    }
    return jwt
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseAttributes(rawAttributes: Any?): Map<String, String> {
    if (rawAttributes == null) {
      return emptyMap()
    }
    val map =
        rawAttributes as? Map<*, *>
            ?: throw InvalidOauthStateException("State attributes must be a JSON object.")
    return map.entries.associate { (key, value) -> key.toString() to value.toString() }
  }

  private fun parseProvider(rawProvider: String?): OauthProviderName? {
    if (rawProvider.isNullOrBlank()) {
      return null
    }
    return try {
      OauthProviderName.valueOf(rawProvider)
    } catch (e: IllegalArgumentException) {
      throw InvalidOauthStateException("State provider value is invalid.", e)
    }
  }

  private fun newStateId(): String {
    val uuidBytes = UUID.randomUUID().toString().toByteArray(StandardCharsets.UTF_8)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(uuidBytes)
  }

  private fun validateAttributes(attributes: Map<String, String>) {
    if (attributes.size > maxAttributesEntryCount) {
      throw InvalidOauthStateException(
          "Too many state attributes. Maximum count is $maxAttributesEntryCount.",
      )
    }

    var totalBytes = 0
    attributes.forEach { (key, value) ->
      if (key.isBlank()) {
        throw InvalidOauthStateException("State attribute key must not be blank.")
      }
      totalBytes += key.toByteArray(StandardCharsets.UTF_8).size
      totalBytes += value.toByteArray(StandardCharsets.UTF_8).size
      if (totalBytes > maxAttributesBytes) {
        throw InvalidOauthStateException(
            "State attributes are too large. Maximum bytes is $maxAttributesBytes.",
        )
      }
    }
  }
}
