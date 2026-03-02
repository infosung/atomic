package com.infosung.atomic.contract.exception

/**
 * Base runtime exception carrying HTTP status for API responses.
 *
 * @property status HTTP status code to return.
 */
open class HttpStatusException(
    val status: Int,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
