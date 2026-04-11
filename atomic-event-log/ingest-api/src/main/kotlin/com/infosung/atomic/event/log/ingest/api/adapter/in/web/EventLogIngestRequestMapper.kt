@file:Suppress("DEPRECATION")

package com.infosung.atomic.event.log.ingest.api.adapter.`in`.web

import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.domain.ClientEventLogPayload
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogPlatformPayload
import com.infosung.atomic.event.log.domain.EventLogPolicy
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.domain.ServerEventLogPayload
import com.infosung.atomic.event.log.domain.WebSocketEventLogPayload
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import java.time.Instant
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.JsonNodeType
import tools.jackson.databind.node.ObjectNode

/** Maps transport DTOs into shallow intake models and fully validated core batches. */
class EventLogIngestRequestMapper(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val policy: EventLogPolicy = EventLogPolicy(),
) : MapEventLogIngestIntakeBatchPort {
  fun toIntakeBatch(request: EventLogBatchIngestRequestDto): EventLogIngestIntakeBatch {
    val serviceId = request.serviceId.orEmpty()
    if (serviceId.isBlank()) {
      throw invalidRequest("serviceId must not be blank.")
    }
    if (serviceId.length > policy.maxIdentifierLength) {
      throw invalidRequest("serviceId length must not exceed ${policy.maxIdentifierLength}.")
    }
    if (request.schemaVersion != policy.supportedSchemaVersion) {
      throw invalidRequest(
          "Unsupported schemaVersion=${request.schemaVersion}. supported=${policy.supportedSchemaVersion}",
      )
    }
    if (request.events.isEmpty()) {
      throw invalidRequest("events must not be empty.")
    }
    if (request.events.size > policy.maxBatchSize) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_BATCH_TOO_LARGE,
          message = "events.size must not exceed ${policy.maxBatchSize}.",
      )
    }
    return EventLogIngestIntakeBatch(
        schemaVersion = request.schemaVersion,
        serviceId = serviceId,
        events =
            request.events.map { event ->
              EventLogIngestIntakeEvent(
                  eventId = event.eventId,
                  eventName = event.eventName,
                  occurredAt = event.occurredAt,
                  platform = event.platform,
                  platformPayloadJson = event.platformPayload?.toString(),
                  eventType = event.eventType,
                  actorId = event.actorId,
                  traceId = event.traceId,
                  tags = event.tags,
                  businessPayloadJson = event.businessPayload?.toString(),
              )
            },
    )
  }

  override fun toCoreBatch(batch: EventLogIngestIntakeBatch): EventLogBatch {
    return EventLogBatch(
        schemaVersion = batch.schemaVersion,
        serviceId = batch.serviceId,
        events = batch.events.mapIndexed(::toEvent),
    )
  }

  fun toResponse(result: EventLogIngestApiResult): EventLogBatchIngestResponseDto {
    return when (result) {
      is EventLogIngestApiResult.Enqueued ->
          EventLogBatchIngestResponseDto(
              serviceId = result.serviceId,
              schemaVersion = result.schemaVersion,
              processingMode = result.mode.name,
              processingStatus = result.processingStatus,
              receiptId = result.receiptId,
              queuedAt = result.queuedAt,
              queuedEventCount = result.queuedEventCount,
          )
      is EventLogIngestApiResult.Completed ->
          EventLogBatchIngestResponseDto(
              serviceId = result.serviceId,
              schemaVersion = result.schemaVersion,
              processingMode = result.mode.name,
              processingStatus = result.processingStatus,
              accepted = result.result.accepted,
              duplicate = result.result.duplicate,
              rejected = result.result.rejected,
              results =
                  result.result.results.map {
                    EventLogEventIngestResultDto(
                        eventId = it.eventId,
                        status = it.status.name,
                        code = it.code?.name,
                    )
                  },
          )
    }
  }

  private fun toEvent(
      index: Int,
      request: EventLogIngestIntakeEvent,
  ): EventLogEvent {
    val occurredAt =
        request.occurredAt?.let { raw ->
          runCatching { Instant.parse(raw) }.getOrNull()
              ?: throw invalidRequest("events[$index].occurredAt is invalid.")
        } ?: throw invalidRequest("events[$index].occurredAt is required.")
    val platform = parsePlatform(request.platform, label = "events[$index].platform")
    return EventLogEvent(
        eventId = request.eventId.orEmpty(),
        eventName = request.eventName.orEmpty(),
        occurredAt = occurredAt,
        platformPayload =
            toPlatformPayload(
                index = index,
                platform = platform,
                rawPlatformPayload = request.platformPayloadJson,
            ),
        eventType =
            request.eventType?.let {
              parseEventType(
                  raw = it,
                  label = "events[$index].eventType",
              )
            },
        actorId = request.actorId,
        traceId = request.traceId,
        tags = request.tags,
        businessPayload =
            toScalarMap(
                node = parseJson(request.businessPayloadJson),
                code = EventLogErrorCode.EVENT_LOG_BUSINESS_PAYLOAD_INVALID,
                label = "events[$index].businessPayload",
            ),
    )
  }

  private fun toPlatformPayload(
      index: Int,
      platform: EventLogPlatform,
      rawPlatformPayload: String?,
  ): EventLogPlatformPayload {
    val node =
        parseJson(rawPlatformPayload)?.takeIf { it.isObject }?.let { it as ObjectNode }
            ?: throw EventLogValidationException(
                code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
                message = "events[$index].platformPayload must be a JSON object.",
            )
    return when {
      platform == EventLogPlatform.API -> toApiPayload(index = index, node = node)
      platform == EventLogPlatform.WEBSOCKET -> toWebSocketPayload(index = index, node = node)
      platform.isClientPlatform() ->
          toClientPayload(index = index, platform = platform, node = node)
      platform == EventLogPlatform.SERVER -> toServerPayload(index = index, node = node)
      else -> throw invalidPlatformPayload("platform is unsupported.")
    }
  }

  private fun parseJson(raw: String?): JsonNode? {
    if (raw == null) {
      return null
    }
    return runCatching { objectMapper.readTree(raw) }
        .getOrElse { throw invalidRequest("payload must be valid JSON.") }
  }

  private fun toApiPayload(
      index: Int,
      node: ObjectNode,
  ): ApiEventLogPayload {
    return ApiEventLogPayload(
        httpMethod = text(node, "httpMethod"),
        endpoint = text(node, "endpoint"),
        status = intOrNull(node, "status", "events[$index].platformPayload.status"),
        executeTimeMs =
            longOrNull(node, "executeTimeMs", "events[$index].platformPayload.executeTimeMs"),
        deviceId = textOrNull(node, "deviceId"),
        clientIpMasked = textOrNull(node, "clientIpMasked"),
        customLang = textOrNull(node, "customLang"),
        acceptLanguage = textOrNull(node, "acceptLanguage"),
        query = textOrNull(node, "query"),
        body = textOrNull(node, "body"),
    )
  }

  private fun toWebSocketPayload(
      index: Int,
      node: ObjectNode,
  ): WebSocketEventLogPayload {
    return WebSocketEventLogPayload(
        channel = text(node, "channel"),
        direction = text(node, "direction"),
        messageType = textOrNull(node, "messageType"),
        connectionId = text(node, "connectionId"),
        sessionId = textOrNull(node, "sessionId"),
        closeCode = intOrNull(node, "closeCode", "events[$index].platformPayload.closeCode"),
        clientIpMasked = textOrNull(node, "clientIpMasked"),
    )
  }

  private fun toClientPayload(
      index: Int,
      platform: EventLogPlatform,
      node: ObjectNode,
  ): ClientEventLogPayload {
    return ClientEventLogPayload(
        platform = platform,
        appId = text(node, "appId"),
        appVersion = text(node, "appVersion"),
        userPseudoId = text(node, "userPseudoId"),
        sessionId =
            long(
                node = node,
                fieldName = "sessionId",
                label = "events[$index].platformPayload.sessionId",
            ),
        engagementTimeMsec =
            longOrNull(
                node = node,
                fieldName = "engagementTimeMsec",
                label = "events[$index].platformPayload.engagementTimeMsec",
            ),
        screenName = textOrNull(node, "screenName"),
        releaseChannel = textOrNull(node, "releaseChannel"),
        buildNumber = textOrNull(node, "buildNumber"),
        locale = textOrNull(node, "locale"),
        timezone = textOrNull(node, "timezone"),
        deviceCategory = textOrNull(node, "deviceCategory"),
        deviceLanguage = textOrNull(node, "deviceLanguage"),
        operatingSystem = textOrNull(node, "operatingSystem"),
        operatingSystemVersion = textOrNull(node, "operatingSystemVersion"),
        deviceModel = textOrNull(node, "deviceModel"),
        deviceBrand = textOrNull(node, "deviceBrand"),
        browser = textOrNull(node, "browser"),
        browserVersion = textOrNull(node, "browserVersion"),
        screenResolution = textOrNull(node, "screenResolution"),
    )
  }

  private fun toServerPayload(
      index: Int,
      node: ObjectNode,
  ): ServerEventLogPayload {
    return ServerEventLogPayload(
        hostName = text(node, "hostName"),
        instanceId = text(node, "instanceId"),
        loggerName = text(node, "loggerName"),
        level = text(node, "level"),
        message = text(node, "message"),
        threadName = textOrNull(node, "threadName"),
        exceptionClass = textOrNull(node, "exceptionClass"),
        exceptionMessage = textOrNull(node, "exceptionMessage"),
    )
  }

  private fun toScalarMap(
      node: JsonNode?,
      code: EventLogErrorCode,
      label: String,
  ): Map<String, EventLogValue> {
    if (node == null || node.isNull) {
      return emptyMap()
    }
    val objectNode =
        node.takeIf { it.isObject } as? ObjectNode
            ?: throw EventLogValidationException(
                code = code,
                message = "$label must be a JSON object.",
            )
    return buildMap {
      val fields = objectNode.properties().iterator()
      while (fields.hasNext()) {
        val entry = fields.next()
        val key = entry.key
        val value = entry.value
        put(key, toScalarValue(value = value, label = "$label.$key", code = code))
      }
    }
  }

  private fun toScalarValue(
      value: JsonNode,
      label: String,
      code: EventLogErrorCode,
  ): EventLogValue {
    return when {
      value.nodeType == JsonNodeType.STRING -> EventLogValue.Text(value.asText())
      value.isIntegralNumber -> EventLogValue.Integer(value.longValue())
      value.isFloatingPointNumber -> EventLogValue.Decimal(value.doubleValue())
      value.isBoolean -> EventLogValue.Flag(value.booleanValue())
      else ->
          throw EventLogValidationException(
              code = code,
              message = "$label must be a scalar string, number, or boolean.",
          )
    }
  }

  private fun parsePlatform(
      raw: String?,
      label: String,
  ): EventLogPlatform {
    return raw?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { EventLogPlatform.valueOf(it.uppercase()) }.getOrNull() }
        ?: throw invalidRequest("$label is invalid.")
  }

  private fun parseEventType(
      raw: String,
      label: String,
  ): EventLogEventType {
    return runCatching { EventLogEventType.valueOf(raw.trim().uppercase()) }.getOrNull()
        ?: throw invalidRequest("$label is invalid.")
  }

  private fun text(
      node: ObjectNode,
      fieldName: String,
  ): String {
    val rawValue = node[fieldName] ?: return ""
    if (rawValue.nodeType != JsonNodeType.STRING) {
      throw invalidPlatformPayload("$fieldName must be a string.")
    }
    return rawValue.asText()
  }

  private fun textOrNull(
      node: ObjectNode,
      fieldName: String,
  ): String? {
    val rawValue = node[fieldName] ?: return null
    if (rawValue.isNull) {
      return null
    }
    if (rawValue.nodeType != JsonNodeType.STRING) {
      throw invalidPlatformPayload("$fieldName must be a string.")
    }
    return rawValue.asText()
  }

  private fun long(
      node: ObjectNode,
      fieldName: String,
      label: String,
  ): Long {
    val rawValue = node[fieldName] ?: return 0L
    if (!rawValue.isIntegralNumber) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
          message = "$label must be an integer.",
      )
    }
    return rawValue.longValue()
  }

  private fun longOrNull(
      node: ObjectNode,
      fieldName: String,
      label: String,
  ): Long? {
    val rawValue = node[fieldName] ?: return null
    if (rawValue.isNull) {
      return null
    }
    if (!rawValue.isIntegralNumber) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
          message = "$label must be an integer.",
      )
    }
    return rawValue.longValue()
  }

  private fun intOrNull(
      node: ObjectNode,
      fieldName: String,
      label: String,
  ): Int? {
    val rawValue = node[fieldName] ?: return null
    if (rawValue.isNull) {
      return null
    }
    if (!rawValue.canConvertToInt()) {
      throw EventLogValidationException(
          code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
          message = "$label must be a 32-bit integer.",
      )
    }
    return rawValue.intValue()
  }

  private fun invalidRequest(message: String): EventLogValidationException {
    return EventLogValidationException(
        code = EventLogErrorCode.EVENT_LOG_REQUEST_INVALID,
        message = message,
    )
  }

  private fun invalidPlatformPayload(message: String): EventLogValidationException {
    return EventLogValidationException(
        code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
        message = "platformPayload.$message",
    )
  }
}
