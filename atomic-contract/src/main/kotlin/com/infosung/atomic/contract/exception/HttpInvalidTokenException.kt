package com.infosung.atomic.contract.exception

class HttpInvalidTokenException(
    message: String = "Invalid token.",
    cause: Throwable? = null,
) : HttpStatusException(status = 401, message = message, cause = cause)
