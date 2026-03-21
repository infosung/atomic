package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.oauth.api.OauthProviderName

interface BuildOauthCallbackRedirectUseCase {
  fun build(
      provider: String,
      code: String,
      state: String,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): CallbackRedirectResult
}

data class CallbackRedirectResult(
    val providerName: OauthProviderName,
    val frontendRedirectUrl: String,
    val redirectTargetType: OauthRedirectClientTarget,
    val relayCodeLength: Int,
)
