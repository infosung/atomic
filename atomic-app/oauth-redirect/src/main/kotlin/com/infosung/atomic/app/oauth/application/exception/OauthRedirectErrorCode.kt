package com.infosung.atomic.app.oauth.application.exception

enum class OauthRedirectErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  OAUTH_REDIRECT_INVALID_REQUEST(400, "OAuth redirect request is invalid."),
  OAUTH_CALLBACK_INVALID_REQUEST(400, "OAuth callback request is invalid."),
  OAUTH_PROVIDER_REMOTE_FAILURE(500, "Upstream OAuth provider request failed."),
  OAUTH_APPLE_CALLBACK_POST_ONLY(400, "Apple callback supports POST form_post only."),
  OAUTH_REDIRECT_CONFIGURATION_INVALID(500, "OAuth redirect configuration is invalid."),
  OAUTH_RELAY_CODE_INVALID_REQUEST(400, "OAuth relay code request is invalid."),
}
