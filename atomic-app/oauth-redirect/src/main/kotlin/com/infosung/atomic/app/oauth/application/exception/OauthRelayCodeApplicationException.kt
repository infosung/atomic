package com.infosung.atomic.app.oauth.application.exception

internal open class OauthRelayCodeApplicationException(
    message: String,
    cause: Throwable? = null,
) : OauthRedirectApplicationException(message, cause)
