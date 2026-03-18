package com.infosung.atomic.app.oauth.adapter.out.redirect

import com.infosung.atomic.app.oauth.AllowedRedirectUriPolicy
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException

internal class AllowedRedirectUriPortAdapter(
    private val properties: AtomicAppOauthRedirectProperties,
) : ValidateOauthRedirectUriPort {
  override fun validateRedirectUri(redirectUri: String): String {
    return try {
      AllowedRedirectUriPolicy.validateRedirectUri(
          redirectUri = redirectUri,
          configuredPrefixes = properties.allowedRedirectUriPrefixes,
      )
    } catch (e: HttpStatusException) {
      throw OauthRedirectRequestException(e.message, e)
    }
  }
}
