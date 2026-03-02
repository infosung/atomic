package com.infosung.atomic.contract.exception

/** HTTP 401 exception for unauthorized requests. */
class HttpUnauthorizedException(
    message: String = "Unauthorized",
    cause: Throwable? = null,
) : HttpStatusException(status = 401, message = message, cause = cause)
