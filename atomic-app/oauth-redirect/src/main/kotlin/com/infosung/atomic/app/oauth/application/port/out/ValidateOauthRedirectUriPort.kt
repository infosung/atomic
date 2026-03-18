package com.infosung.atomic.app.oauth.application.port.out

internal interface ValidateOauthRedirectUriPort {
  fun validateRedirectUri(redirectUri: String): String
}
