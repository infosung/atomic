package com.infosung.atomic.app.oauth.application.exception

open class OauthRelayCodeApplicationException(
    errorCode: OauthRedirectErrorCode,
    message: String,
    cause: Throwable? = null,
) : OauthRedirectApplicationException(errorCode, message, cause)
