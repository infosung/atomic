package com.infosung.atomic.app.oauth

import com.infosung.atomic.oauth.api.OauthProviderName

/** One-time OAuth relay payload consumed by login API using relayCode. */
data class OauthRelayPayload(
    val provider: OauthProviderName,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val scopes: Set<String> = emptySet(),
    val raw: Map<String, Any?> = emptyMap(),
    val nonce: String? = null,
    val stateAttributes: Map<String, String> = emptyMap(),
)
