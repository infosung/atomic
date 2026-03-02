package com.infosung.atomic.oauth.provider.google

/** Google token endpoint response payload. */
data class GoogleExchangeTokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val scope: String? = null,
    val token_type: String? = null,
    val id_token: String? = null,
)

/** Google user-info endpoint response payload. */
data class GoogleUserInfoResponse(
    val sub: String? = null,
    val id: String? = null,
    val email: String? = null,
    val verified_email: Boolean? = null,
    val email_verified: Boolean? = null,
    val name: String? = null,
    val given_name: String? = null,
    val family_name: String? = null,
    val picture: String? = null,
    val hd: String? = null,
)
