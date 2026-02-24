package com.infosung.atomic.contract.exception

open class HttpStatusException(
    val status: Int,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
