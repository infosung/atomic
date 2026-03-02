package com.infosung.atomic.contract.header

import java.util.UUID

/**
 * Normalized request header values consumed by Atomic components.
 *
 * @property platform Client platform string (for example `android`, `ios`, `web`).
 * @property deviceId Client device identifier.
 * @property appVersion Client app version.
 * @property userAgent Raw `User-Agent` header value.
 * @property clientIp Resolved client IP.
 * @property acceptLanguage Raw `Accept-Language` header value.
 * @property customLang Custom language header value.
 * @property traceId Request trace identifier.
 */
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
