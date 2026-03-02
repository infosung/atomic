package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException

/** Exception used when servlet filter processing fails. */
class HttpFilterProcessingException(
    val method: String?,
    val uri: String?,
    status: Int = 500,
    override val cause: Throwable? = null,
) :
    HttpStatusException(
        status = if (status >= 400) status else 500,
        message = "Filter processing failed: method=$method, uri=$uri",
        cause = cause,
    )
