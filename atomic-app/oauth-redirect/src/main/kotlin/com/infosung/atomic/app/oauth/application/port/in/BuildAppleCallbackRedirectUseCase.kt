package com.infosung.atomic.app.oauth.application.port.`in`

internal interface BuildAppleCallbackRedirectUseCase {
  fun build(
      state: String,
      idToken: String,
      code: String?,
      user: String?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): CallbackRedirectResult
}
