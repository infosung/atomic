package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException

class HttpRequestExecutionException(
    val method: String?,
    val url: String,
    override val cause: Throwable? = null,
) :
    HttpStatusException(
        status = 500,
        message = "Request execution failed: method=$method, url=$url",
        cause = cause,
    )
