package com.infosung.atomic.app.oauth.application.exception

internal class OauthRedirectRemoteFailureException(
    message: String,
    cause: Throwable? = null,
) : OauthRedirectApplicationException(message, cause)
