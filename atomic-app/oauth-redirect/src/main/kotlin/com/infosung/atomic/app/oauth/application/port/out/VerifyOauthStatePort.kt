package com.infosung.atomic.app.oauth.application.port.out

import com.infosung.atomic.oauth.api.OauthProviderName
import org.springframework.security.oauth2.jwt.Jwt

internal interface VerifyOauthStatePort {
  fun verifyState(
      signedState: String,
      expectedProvider: OauthProviderName,
  ): Jwt
}
