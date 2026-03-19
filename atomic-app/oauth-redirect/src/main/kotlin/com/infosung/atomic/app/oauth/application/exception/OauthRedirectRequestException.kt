package com.infosung.atomic.app.oauth.application.exception

internal class OauthRedirectRequestException(
    message: String,
    cause: Throwable? = null,
) : OauthRedirectApplicationException(message, cause)
