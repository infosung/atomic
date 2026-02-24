package com.infosung.atomic.contract.exception

class HttpUnauthorizedException(
    message: String = "Unauthorized",
    cause: Throwable? = null,
) : HttpStatusException(status = 401, message = message, cause = cause)
