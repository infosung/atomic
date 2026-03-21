package com.infosung.atomic.app.oauth.application.exception

open class OauthRedirectApplicationException(
    val errorCode: OauthRedirectErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
