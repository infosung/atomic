package com.infosung.atomic.app.oauth.application.exception

open class OauthRedirectApplicationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
