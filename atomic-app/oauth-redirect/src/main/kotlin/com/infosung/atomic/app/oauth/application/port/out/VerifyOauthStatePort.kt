package com.infosung.atomic.app.oauth.application.port.out

import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.oauth.api.OauthProviderName

internal interface VerifyOauthStatePort {
  fun verifyState(
      signedState: String,
      expectedProvider: OauthProviderName,
  ): OauthVerifiedState
}
