package com.infosung.atomic.app.oauth.application.exception

class OauthRedirectRemoteFailureException(
    message: String,
    cause: Throwable? = null,
) : OauthRedirectApplicationException(message, cause)
