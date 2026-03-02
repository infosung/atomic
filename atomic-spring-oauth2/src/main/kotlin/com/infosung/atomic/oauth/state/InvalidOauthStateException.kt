package com.infosung.atomic.oauth.state

import com.infosung.atomic.oauth.exception.OauthException

/**
 * Exception raised when OAuth state token is invalid, expired, mismatched, or reused.
 */
class InvalidOauthStateException(
    message: String,
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)
