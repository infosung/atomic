package com.infosung.atomic.event.log.ingest.api.application.model

import java.io.Serializable

/**
 * Shallow-validated batch captured at the receive plane.
 *
 * This model intentionally preserves raw transport fields so that full platform validation can be
 * deferred to the async process plane.
 */
data class EventLogIngestIntakeBatch(
    val schemaVersion: Int = 1,
    val serviceId: String,
    val events: List<EventLogIngestIntakeEvent>,
) : Serializable

data class EventLogIngestIntakeEvent(
    val eventId: String? = null,
    val eventName: String? = null,
    val occurredAt: String? = null,
    val platform: String? = null,
    val platformPayloadJson: String? = null,
    val eventType: String? = null,
    val actorId: String? = null,
    val traceId: String? = null,
    val tags: Set<String> = emptySet(),
    val businessPayloadJson: String? = null,
) : Serializable
