package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.exception.OauthException

class InvalidOauthStateException(
    message: String,
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)
