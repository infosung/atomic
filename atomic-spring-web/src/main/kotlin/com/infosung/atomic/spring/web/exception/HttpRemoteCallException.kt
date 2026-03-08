package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException

/** Exception representing non-success upstream HTTP responses. */
class HttpRemoteCallException(
    status: Int,
    val method: String?,
    val url: String,
    val responseBody: String? = null,
    override val cause: Throwable? = null,
) :
    HttpStatusException(
        status = status,
        message = "Remote call failed: status=$status, method=$method",
        cause = cause,
    )
