package com.infosung.atomic.oauth.exception

import com.infosung.atomic.contract.exception.HttpStatusException

class HttpJwtVerifyException(
    message: String = "JWT verification failed.",
    cause: Throwable? = null,
) : HttpStatusException(status = 400, message = message, cause = cause)

class HttpIOException(
    message: String = "I/O error.",
    cause: Throwable? = null,
) : HttpStatusException(status = 500, message = message, cause = cause)
