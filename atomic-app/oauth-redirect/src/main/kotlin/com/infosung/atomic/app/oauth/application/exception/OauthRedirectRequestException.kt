package com.infosung.atomic.app.oauth.application.exception

class OauthRedirectRequestException(
    message: String,
    cause: Throwable? = null,
    errorCode: OauthRedirectErrorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
) : OauthRedirectApplicationException(errorCode, message, cause)
