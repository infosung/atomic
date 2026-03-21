package com.infosung.atomic.app.oauth.application.exception

class OauthRelayCodeRequestException(
    message: String,
    cause: Throwable? = null,
    errorCode: OauthRedirectErrorCode = OauthRedirectErrorCode.OAUTH_RELAY_CODE_INVALID_REQUEST,
) : OauthRelayCodeApplicationException(errorCode, message, cause)
