package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.OauthRedirectClientTarget
import com.infosung.atomic.oauth.api.OauthProviderName

internal interface BuildAuthorizationRedirectUseCase {
  fun build(
      provider: String,
      redirectUri: String,
      nonce: String?,
      prompt: String?,
      loginHint: String?,
      responseMode: String?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): AuthorizationRedirectResult
}

internal data class AuthorizationRedirectResult(
    val providerName: OauthProviderName,
    val authorizationUrl: String,
    val redirectTargetType: OauthRedirectClientTarget,
)
