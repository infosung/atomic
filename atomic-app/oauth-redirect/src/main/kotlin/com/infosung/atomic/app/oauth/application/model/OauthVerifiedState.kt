package com.infosung.atomic.app.oauth.application.model

import com.infosung.atomic.oauth.api.OauthProviderName

internal data class OauthVerifiedState(
    val provider: OauthProviderName?,
    val redirectUri: String?,
    val nonce: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
