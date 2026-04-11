package com.infosung.atomic.event.log.ingest.api.application.model

import java.io.Serializable

/** Request metadata resolved at the HTTP boundary before core ingest runs. */
data class EventLogIngestApiRequestMetadata(
    val method: String? = null,
    val requestUri: String? = null,
    val clientIp: String? = null,
    val userAgent: String? = null,
    val headers: Map<String, String> = emptyMap(),
) : Serializable
