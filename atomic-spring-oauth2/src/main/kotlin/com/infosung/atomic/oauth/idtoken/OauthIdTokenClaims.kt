package com.infosung.atomic.oauth.idtoken

import java.time.Instant

/** Transport-agnostic verified id-token claims extracted from a validated JWT. */
data class OauthIdTokenClaims(
    val issuer: String,
    val subject: String?,
    val audiences: List<String>,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
    val nonce: String? = null,
    val claims: Map<String, Any?> = emptyMap(),
) {
  /** Convenience accessor for optional string claims used by provider adapters. */
  fun stringClaim(name: String): String? = claims[name] as? String
}
