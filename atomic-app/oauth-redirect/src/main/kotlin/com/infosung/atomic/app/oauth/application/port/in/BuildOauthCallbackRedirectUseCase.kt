package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.OauthRedirectClientTarget
import com.infosung.atomic.oauth.api.OauthProviderName

internal interface BuildOauthCallbackRedirectUseCase {
  fun build(
      provider: String,
      code: String,
      state: String,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): CallbackRedirectResult
}

internal data class CallbackRedirectResult(
    val providerName: OauthProviderName,
    val frontendRedirectUrl: String,
    val redirectTargetType: OauthRedirectClientTarget,
    val relayCodeLength: Int,
)
