package com.infosung.atomic.oauth.exception

import com.infosung.atomic.contract.exception.HttpStatusException

/**
 * HTTP 400 exception for id-token verification failures.
 */
class HttpJwtVerifyException(
    message: String = "JWT verification failed.",
    cause: Throwable? = null,
) : HttpStatusException(status = 400, message = message, cause = cause)

/**
 * HTTP 500 exception for remote I/O failures.
 */
class HttpIOException(
    message: String = "I/O error.",
    cause: Throwable? = null,
) : HttpStatusException(status = 500, message = message, cause = cause)
