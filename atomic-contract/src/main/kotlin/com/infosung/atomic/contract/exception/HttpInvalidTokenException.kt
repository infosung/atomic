package com.infosung.atomic.contract.exception

/** HTTP 401 exception for invalid access/refresh tokens. */
class HttpInvalidTokenException(
    message: String = "Invalid token.",
    cause: Throwable? = null,
) : HttpStatusException(status = 401, message = message, cause = cause)
