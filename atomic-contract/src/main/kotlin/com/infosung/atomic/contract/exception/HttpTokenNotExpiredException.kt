package com.infosung.atomic.contract.exception

/**
 * HTTP 400 exception when token refresh is requested before token expiration.
 */
class HttpTokenNotExpiredException(
    message: String = "Token is not expired yet.",
    cause: Throwable? = null,
) : HttpStatusException(status = 400, message = message, cause = cause)
