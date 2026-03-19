package com.infosung.atomic.app.oauth.adapter.out.redirect

import com.infosung.atomic.app.oauth.AllowedRedirectUriPolicy
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties

internal class AllowedRedirectUriPortAdapter(
    private val properties: AtomicAppOauthRedirectProperties,
) : ValidateOauthRedirectUriPort {
  override fun validateRedirectUri(redirectUri: String): String {
    return AllowedRedirectUriPolicy.validateRedirectUri(
        redirectUri = redirectUri,
        configuredPrefixes = properties.allowedRedirectUriPrefixes,
    )
  }
}
