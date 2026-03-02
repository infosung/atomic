package com.infosung.atomic.oauth.provider.kakao

/**
 * Kakao token endpoint response payload.
 */
data class KakaoExchangeTokenResponse(
    val token_type: String? = null,
    val access_token: String? = null,
    val id_token: String? = null,
    val expires_in: Long? = null,
    val refresh_token: String? = null,
    val refresh_token_expires_in: Long? = null,
    val scope: String? = null,
)

/**
 * Kakao user-info endpoint response payload.
 */
data class KakaoUserInfoResponse(
    val id: Long? = null,
    val has_signed_up: Boolean? = null,
    val connected_at: String? = null,
    val properties: Map<String, Any?>? = null,
    val kakao_account: Map<String, Any?>? = null,
    val synched_at: String? = null,
    val for_partner: Map<String, Any?>? = null,
)
