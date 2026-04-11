package com.infosung.atomic.event.log.ingest.api.adapter.`in`.web

import java.time.Instant
import tools.jackson.databind.JsonNode

data class EventLogBatchIngestRequestDto(
    val schemaVersion: Int = 1,
    val serviceId: String? = null,
    val events: List<EventLogEventRequestDto> = emptyList(),
)

data class EventLogEventRequestDto(
    val eventId: String? = null,
    val eventName: String? = null,
    val occurredAt: String? = null,
    val platform: String? = null,
    val platformPayload: JsonNode? = null,
    val eventType: String? = null,
    val actorId: String? = null,
    val traceId: String? = null,
    val tags: Set<String> = emptySet(),
    val businessPayload: JsonNode? = null,
)

data class EventLogBatchIngestResponseDto(
    val serviceId: String,
    val schemaVersion: Int,
    val processingMode: String,
    val processingStatus: String,
    val receiptId: String? = null,
    val queuedAt: Instant? = null,
    val queuedEventCount: Int? = null,
    val accepted: Int? = null,
    val duplicate: Int? = null,
    val rejected: Int? = null,
    val results: List<EventLogEventIngestResultDto>? = null,
)

data class EventLogEventIngestResultDto(
    val eventId: String,
    val status: String,
    val code: String? = null,
)
