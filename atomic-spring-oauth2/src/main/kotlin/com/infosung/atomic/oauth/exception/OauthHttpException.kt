package com.infosung.atomic.oauth.exception

/** OAuth exception for id-token verification failures. */
class HttpJwtVerifyException(
    message: String = "JWT verification failed.",
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)

/** OAuth exception for remote I/O failures. */
class HttpIOException(
    message: String = "I/O error.",
    cause: Throwable? = null,
) : OauthException(message = message, cause = cause)
