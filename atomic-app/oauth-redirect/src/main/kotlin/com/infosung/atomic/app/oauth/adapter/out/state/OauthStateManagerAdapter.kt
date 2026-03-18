package com.infosung.atomic.app.oauth.adapter.out.state

import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.state.OauthStateManager
import org.springframework.security.oauth2.jwt.Jwt

internal class OauthStateManagerAdapter(
    private val oauthStateManager: OauthStateManager,
) : VerifyOauthStatePort {
  override fun verifyState(
      signedState: String,
      expectedProvider: OauthProviderName,
  ): Jwt {
    return oauthStateManager.verifyState(
        signedState = signedState,
        expectedProvider = expectedProvider,
    )
  }
}
