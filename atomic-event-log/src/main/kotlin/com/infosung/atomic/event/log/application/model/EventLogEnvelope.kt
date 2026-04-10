package com.infosung.atomic.event.log.application.model

import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogPlatformPayload
import com.infosung.atomic.event.log.domain.EventLogValue
import java.io.Serializable
import java.time.Instant

/** One event carried by the common envelope. */
data class EventLogEvent(
    val eventId: String,
    val eventName: String,
    val occurredAt: Instant,
    val platformPayload: EventLogPlatformPayload,
    val eventType: EventLogEventType? = null,
    val actorId: String? = null,
    val traceId: String? = null,
    val tags: Set<String> = emptySet(),
    val businessPayload: Map<String, EventLogValue> = emptyMap(),
) : Serializable

/** Batch envelope accepted by the core ingest service. */
data class EventLogBatch(
    val schemaVersion: Int = 1,
    val serviceId: String,
    val events: List<EventLogEvent>,
) : Serializable

/** Context resolved outside the core and attached at ingest time. */
data class EventLogIngestContext(
    val receivedAt: Instant? = null,
    val collectorId: String? = null,
) : Serializable

/** Sanitized record passed to persistence/export adapters. */
data class EventLogRecord(
    val schemaVersion: Int,
    val serviceId: String,
    val eventId: String,
    val eventName: String,
    val eventType: EventLogEventType? = null,
    val platform: EventLogPlatform,
    val occurredAt: Instant,
    val receivedAt: Instant,
    val actorId: String? = null,
    val traceId: String? = null,
    val tags: Set<String> = emptySet(),
    val platformPayload: Map<String, EventLogValue> = emptyMap(),
    val businessPayload: Map<String, EventLogValue> = emptyMap(),
) : Serializable
