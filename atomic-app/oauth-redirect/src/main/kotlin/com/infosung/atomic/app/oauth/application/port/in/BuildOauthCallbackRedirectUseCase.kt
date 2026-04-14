package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.oauth.api.OauthProviderName

/**
 * Public override seam for hosts that replace the default callback web adapter.
 *
 * `AppOauthRedirectController` reads callback-binding and PKCE verifier cookies on behalf of the
 * HTTP contract. Direct callers may pass `callbackBindingToken` / `codeVerifier` explicitly when
 * they intentionally bypass the default controller.
 */
interface BuildOauthCallbackRedirectUseCase {
  fun build(
      provider: String,
      code: String,
      state: String,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
      codeVerifier: String? = null,
  ): CallbackRedirectResult
}

data class CallbackRedirectResult(
    val providerName: OauthProviderName,
    val frontendRedirectUrl: String,
    val redirectTargetType: OauthRedirectClientTarget,
    val relayCodeLength: Int,
)
