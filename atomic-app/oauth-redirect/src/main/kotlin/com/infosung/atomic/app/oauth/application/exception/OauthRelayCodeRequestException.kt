package com.infosung.atomic.app.oauth.application.exception

internal class OauthRelayCodeRequestException(
    message: String,
    cause: Throwable? = null,
) : OauthRelayCodeApplicationException(message, cause)
