package com.infosung.atomic.oauth.api

enum class OauthProviderName {
  KAKAO,
  GOOGLE,
  APPLE,
}

enum class OauthIdentityStrategy {
  AUTO,
  ID_TOKEN,
  USER_INFO_API,
}

enum class OauthScopePreset {
  ID_ONLY,
  BASIC_PROFILE,
  FULL_PROFILE,
}

enum class OauthIdentityPayloadMode {
  ID_ONLY,
  BASIC_PROFILE,
  FULL_PROFILE,
}

data class OauthAuthorizationRequest(
    // Client destination URI to store in signed state.
    // This is used by your server after callback, not as provider redirect_uri.
    val redirectUri: String? = null,
    val scopes: Set<String> = emptySet(),
    val scopePreset: OauthScopePreset? = null,
    val nonce: String? = null,
    val prompt: String? = null,
    val loginHint: String? = null,
    val responseMode: String? = null,
    val stateAttributes: Map<String, String> = emptyMap(),
    val additionalParameters: Map<String, String> = emptyMap(),
)

data class OauthTokenExchangeRequest(
    val code: String,
    // Required callback state to prevent CSRF and correlate signed state payload.
    val state: String,
    val scopes: Set<String> = emptySet(),
    val scopePreset: OauthScopePreset? = null,
    val additionalParameters: Map<String, String> = emptyMap(),
)

data class OauthTokenRefreshRequest(
    val refreshToken: String,
    val accessToken: String? = null,
    val scopes: Set<String> = emptySet(),
    val scopePreset: OauthScopePreset? = null,
    val additionalParameters: Map<String, String> = emptyMap(),
)

data class OauthTokenRevokeRequest(
    val accessToken: String,
    val additionalParameters: Map<String, String> = emptyMap(),
)

data class OauthIdentityRequest(
    val strategy: OauthIdentityStrategy = OauthIdentityStrategy.AUTO,
    val accessToken: String? = null,
    val idToken: String? = null,
    val audience: String? = null,
    val scopes: Set<String> = emptySet(),
    val scopePreset: OauthScopePreset? = null,
    val payloadMode: OauthIdentityPayloadMode = OauthIdentityPayloadMode.FULL_PROFILE,
    val nonce: String? = null,
    val userInfoEndpoint: String? = null,
    val userInfoParameters: Map<String, String> = emptyMap(),
    val additionalParameters: Map<String, String> = emptyMap(),
)

data class OauthTokenResult(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val idToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val scopes: Set<String> = emptySet(),
    val raw: Map<String, Any?> = emptyMap(),
)

data class OauthIdentityResult(
    val provider: OauthProviderName,
    val userId: String,
    val email: String? = null,
    val displayName: String? = null,
    val pictureUrl: String? = null,
    val scopes: Set<String> = emptySet(),
    val payloadMode: OauthIdentityPayloadMode = OauthIdentityPayloadMode.FULL_PROFILE,
    val claims: Map<String, Any?> = emptyMap(),
    val rawProfile: Map<String, Any?> = emptyMap(),
)
