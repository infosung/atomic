package com.infosung.atomic.app.oauth.application.port.`in`

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTarget
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import com.infosung.atomic.oauth.api.OauthProviderName

/**
 * Public override seam for hosts that replace the default redirect web adapter.
 *
 * `AppOauthRedirectController` intentionally exposes a stricter HTTP contract and manages
 * callback-binding/PKCE material itself. Direct callers of this use-case may still provide
 * caller-managed `codeVerifier` and `callbackBindingToken` when they intentionally own that
 * orchestration boundary.
 */
interface BuildAuthorizationRedirectUseCase {
  fun build(
      provider: String,
      redirectUri: String,
      nonce: String?,
      prompt: String?,
      loginHint: String?,
      responseMode: String?,
      codeVerifier: String? = null,
      codeChallengeMethod: OauthCodeChallengeMethod? = null,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): AuthorizationRedirectResult
}

data class AuthorizationRedirectResult(
    val providerName: OauthProviderName,
    val authorizationUrl: String,
    val redirectTargetType: OauthRedirectClientTarget,
)
