package com.infosung.atomic.event.log.application.service

import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.model.EventLogEventIngestResult
import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.application.model.EventLogIngestResult
import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.model.EventLogStatus
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.domain.EventLogPolicy
import com.infosung.atomic.event.log.domain.EventLogValue
import java.time.Instant

/** Validates, masks, and appends shared event-log batches. */
class EventLogIngestionService(
    private val store: EventLogStore,
    private val policy: EventLogPolicy = EventLogPolicy(),
) {
  private val log = System.getLogger(EventLogIngestionService::class.java.name)

  fun ingest(
      batch: EventLogBatch,
      context: EventLogIngestContext = EventLogIngestContext(),
  ): EventLogIngestResult {
    validateBatch(batch)
    log.log(
        System.Logger.Level.DEBUG,
        "Event log ingest started: serviceId={0}, eventCount={1}",
        batch.serviceId,
        batch.events.size,
    )

    val records = mutableListOf<EventLogRecord>()
    val provisionalResults = mutableListOf<EventLogEventIngestResult>()
    val receivedAt = context.receivedAt ?: Instant.now()

    batch.events.forEach { event ->
      val prepared = prepareEvent(event)
      if (prepared is EventPreparationRejected) {
        provisionalResults +=
            EventLogEventIngestResult(
                eventId = event.eventId,
                status = EventLogStatus.REJECTED,
                code = prepared.code,
            )
        return@forEach
      }

      prepared as EventPreparationAccepted
      records +=
          EventLogRecord(
              schemaVersion = batch.schemaVersion,
              serviceId = batch.serviceId,
              eventId = event.eventId,
              eventName = event.eventName,
              eventType = event.eventType,
              platform = event.platformPayload.platform,
              occurredAt = event.occurredAt,
              receivedAt = receivedAt,
              actorId = event.actorId,
              traceId = event.traceId,
              tags = event.tags,
              platformPayload = prepared.platformPayload,
              businessPayload = prepared.businessPayload,
          )
      provisionalResults +=
          EventLogEventIngestResult(
              eventId = event.eventId,
              status = EventLogStatus.ACCEPTED,
          )
    }

    val appendResults =
        if (records.isEmpty()) {
          emptyList()
        } else {
          store.append(records)
        }
    require(appendResults.size == records.size) {
      "EventLogStore must return one append result per input record."
    }

    var acceptedIndex = 0
    val finalResults =
        provisionalResults.map { provisional ->
          if (provisional.status == EventLogStatus.REJECTED) {
            provisional
          } else {
            when (appendResults[acceptedIndex++]) {
              EventLogStoreAppendResult.ACCEPTED ->
                  provisional.copy(status = EventLogStatus.ACCEPTED)
              EventLogStoreAppendResult.DUPLICATE ->
                  provisional.copy(status = EventLogStatus.DUPLICATE)
            }
          }
        }

    val result =
        EventLogIngestResult(
            accepted = finalResults.count { it.status == EventLogStatus.ACCEPTED },
            duplicate = finalResults.count { it.status == EventLogStatus.DUPLICATE },
            rejected = finalResults.count { it.status == EventLogStatus.REJECTED },
            results = finalResults,
        )
    log.log(
        System.Logger.Level.DEBUG,
        "Event log ingest finished: serviceId={0}, accepted={1}, duplicate={2}, rejected={3}",
        batch.serviceId,
        result.accepted,
        result.duplicate,
        result.rejected,
    )
    return result
  }

  private fun validateBatch(batch: EventLogBatch) {
    if (batch.serviceId.isBlank()) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_REQUEST_INVALID,
          message = "serviceId must not be blank.",
      )
    }
    if (batch.schemaVersion != policy.supportedSchemaVersion) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_REQUEST_INVALID,
          message =
              "Unsupported schemaVersion=${batch.schemaVersion}. supported=${policy.supportedSchemaVersion}",
      )
    }
    if (batch.events.isEmpty()) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_REQUEST_INVALID,
          message = "events must not be empty.",
      )
    }
    if (batch.events.size > policy.maxBatchSize) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_BATCH_TOO_LARGE,
          message = "events.size must not exceed ${policy.maxBatchSize}.",
      )
    }
    validateIdentifier(batch.serviceId, "serviceId")
  }

  private fun prepareEvent(event: EventLogEvent): EventPreparation {
    if (event.eventId.isBlank()) {
      return EventPreparationRejected(EventLogErrorCode.EVENT_LOG_EVENT_ID_REQUIRED)
    }
    if (event.eventId.length > policy.maxIdentifierLength) {
      return EventPreparationRejected(EventLogErrorCode.EVENT_LOG_EVENT_ID_REQUIRED)
    }
    if (event.eventName.isBlank() ||
        event.eventName.length > policy.maxEventNameLength ||
        !policy.allowedEventNamePattern.matches(event.eventName)) {
      return EventPreparationRejected(EventLogErrorCode.EVENT_LOG_EVENT_NAME_INVALID)
    }
    if (event.platformPayload.validate(policy) != null) {
      return EventPreparationRejected(EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID)
    }
    val platformFields = event.platformPayload.toFields()
    if (!validatePayload(platformFields) || !validatePayload(event.businessPayload)) {
      return EventPreparationRejected(EventLogErrorCode.EVENT_LOG_BUSINESS_PAYLOAD_INVALID)
    }
    return EventPreparationAccepted(
        platformPayload = sanitizePayload(platformFields),
        businessPayload = sanitizePayload(event.businessPayload),
    )
  }

  private fun validatePayload(payload: Map<String, EventLogValue>): Boolean {
    if (payload.size > policy.maxPayloadEntries) {
      return false
    }
    return payload.all { (key, value) ->
      key.isNotBlank() && key.length <= policy.maxIdentifierLength && validateValue(value)
    }
  }

  private fun validateValue(value: EventLogValue): Boolean =
      when (value) {
        is EventLogValue.Text -> value.value.length <= policy.maxTextLength
        is EventLogValue.Integer -> true
        is EventLogValue.Decimal -> true
        is EventLogValue.Flag -> true
      }

  private fun sanitizePayload(payload: Map<String, EventLogValue>): Map<String, EventLogValue> {
    if (payload.isEmpty()) {
      return payload
    }
    var sanitized: MutableMap<String, EventLogValue>? = null
    payload.keys.forEach { key ->
      if (policy.sensitiveKeyPattern.containsMatchIn(key)) {
        val mutablePayload = sanitized ?: payload.toMutableMap().also { sanitized = it }
        mutablePayload[key] = EventLogValue.Text(MASKED_VALUE)
      }
    }
    return sanitized ?: payload
  }

  private fun validateIdentifier(
      value: String,
      label: String,
  ) {
    if (value.length > policy.maxIdentifierLength) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_REQUEST_INVALID,
          message = "$label length must not exceed ${policy.maxIdentifierLength}.",
      )
    }
  }

  private sealed interface EventPreparation

  private data class EventPreparationAccepted(
      val platformPayload: Map<String, EventLogValue>,
      val businessPayload: Map<String, EventLogValue>,
  ) : EventPreparation

  private data class EventPreparationRejected(
      val code: EventLogErrorCode,
  ) : EventPreparation

  private companion object {
    const val MASKED_VALUE = "***"
  }
}
