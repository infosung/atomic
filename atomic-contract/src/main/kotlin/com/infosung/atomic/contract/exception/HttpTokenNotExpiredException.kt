package com.infosung.atomic.contract.exception

class HttpTokenNotExpiredException(
    message: String = "Token is not expired yet.",
    cause: Throwable? = null,
) : HttpStatusException(status = 400, message = message, cause = cause)
