package com.infosung.atomic.contract.header

import java.util.UUID

data class ApiHeaderDto(
    val platform: String? = null,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val userAgent: String? = null,
    val clientIp: String? = null,
    val acceptLanguage: String? = null,
    val customLang: String? = null,
    val traceId: String = UUID.randomUUID().toString(),
)
