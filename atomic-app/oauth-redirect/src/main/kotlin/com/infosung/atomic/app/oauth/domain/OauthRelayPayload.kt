package com.infosung.atomic.app.oauth.domain

import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthProviderName

/**
 * One-time OAuth relay payload consumed by login API using relayCode.
 *
 * `resolvedIdentity` is an optional convenience snapshot that reuses the public oauth identity
 * model. Host applications should persist only the fields they own (for example `providerSubject`,
 * `emailVerified`, selected profile attributes) rather than coupling their own storage schema to
 * the full snapshot object.
 */
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
    val resolvedIdentity: OauthIdentityResult? = null,
)
