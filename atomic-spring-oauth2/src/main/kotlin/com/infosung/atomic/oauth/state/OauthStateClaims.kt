package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Instant

/** Transport-agnostic verified state claims extracted from a signed OAuth state token. */
data class OauthStateClaims(
    val issuer: String,
    val stateId: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val provider: OauthProviderName? = null,
    val redirectUri: String? = null,
    val nonce: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
