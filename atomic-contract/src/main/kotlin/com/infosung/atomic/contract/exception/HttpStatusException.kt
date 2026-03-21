package com.infosung.atomic.contract.exception

/**
 * Base runtime exception carrying HTTP status for API responses.
 *
 * @property status HTTP status code to return.
 * @property code Stable machine-readable error code used on the wire when present.
 */
open class HttpStatusException(
    val status: Int,
    override val message: String,
    override val cause: Throwable? = null,
    val code: String? = null,
) : RuntimeException(message, cause)
