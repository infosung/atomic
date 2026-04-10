package com.infosung.atomic.event.log.spring.web.adapter.`in`.web

import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.service.EventLogIngestionService
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.spring.web.log.LogSaver
import com.infosung.atomic.spring.web.log.ServiceApiRequestLog
import com.infosung.atomic.spring.web.log.ServiceApiResponseLog
import com.infosung.atomic.spring.web.log.ServiceLog
import java.time.Instant
import kotlin.math.max

/** Maps atomic-spring-web API logs into the shared event-log envelope. */
class AtomicSpringWebEventLogSaver(
    private val serviceId: String,
    private val ingestionService: EventLogIngestionService,
    private val eventIdGenerator: AtomicSpringWebEventIdGenerator =
        DefaultAtomicSpringWebEventIdGenerator(),
) : LogSaver {
  private val log = System.getLogger(AtomicSpringWebEventLogSaver::class.java.name)

  override fun saveAll(logs: List<ServiceLog>) {
    if (logs.isEmpty()) {
      return
    }
    val batch = EventLogBatch(serviceId = serviceId, events = logs.map(::toEvent))
    val result = ingestionService.ingest(batch)
    log.log(
        System.Logger.Level.DEBUG,
        "Spring-web event log save finished: serviceId={0}, accepted={1}, duplicate={2}, rejected={3}",
        serviceId,
        result.accepted,
        result.duplicate,
        result.rejected,
    )
    if (result.rejected > 0) {
      throw IllegalStateException(
          "atomic-spring-web produced rejected event logs. serviceId=$serviceId rejected=${result.rejected}")
    }
  }

  private fun toEvent(logEntry: ServiceLog): EventLogEvent =
      when (logEntry) {
        is ServiceApiRequestLog ->
            EventLogEvent(
                eventId = eventIdGenerator.generate(serviceId, logEntry, "api.request"),
                eventName = "api.request",
                occurredAt = Instant.ofEpochMilli(logEntry.logTime),
                eventType = EventLogEventType.REQUEST,
                actorId = logEntry.userId,
                traceId = logEntry.traceId,
                tags = setOf("api", "request"),
                platformPayload =
                    ApiEventLogPayload(
                        httpMethod = logEntry.httpMethod,
                        endpoint = logEntry.endPoint,
                        deviceId = logEntry.deviceId,
                        clientIpMasked = maskIp(logEntry.clientIp),
                        customLang = logEntry.customLang,
                        acceptLanguage = logEntry.acceptLanguage,
                        query = logEntry.query,
                        body = logEntry.body,
                    ),
            )
        is ServiceApiResponseLog ->
            EventLogEvent(
                eventId = eventIdGenerator.generate(serviceId, logEntry, "api.response"),
                eventName = "api.response",
                occurredAt = Instant.ofEpochMilli(logEntry.logTime),
                eventType = EventLogEventType.RESPONSE,
                actorId = logEntry.userId,
                traceId = logEntry.traceId,
                tags = setOf("api", "response"),
                platformPayload =
                    ApiEventLogPayload(
                        httpMethod = logEntry.httpMethod,
                        endpoint = logEntry.endPoint,
                        status = logEntry.status,
                        executeTimeMs = logEntry.executeTime,
                        deviceId = logEntry.deviceId,
                        clientIpMasked = maskIp(logEntry.clientIp),
                    ),
            )
        else ->
            throw IllegalArgumentException(
                "Unsupported ServiceLog type: ${logEntry::class.java.name}")
      }

  private fun maskIp(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = normalizeIpCandidate(value)
    return maskIpv4(normalized) ?: maskIpv6(normalized) ?: "***"
  }

  private fun normalizeIpCandidate(value: String): String {
    val withoutScope = value.substringBefore('%')
    if (withoutScope.startsWith('[') && withoutScope.contains(']')) {
      return withoutScope.substringAfter('[').substringBefore(']')
    }
    return withoutScope
  }

  private fun maskIpv4(value: String): String? {
    val segments = value.split('.')
    if (segments.size != 4) {
      return null
    }
    val octets = mutableListOf<Int>()
    for (segment in segments) {
      val octet = segment.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
      octets += octet
    }
    return "${octets[0]}.${octets[1]}.${octets[2]}.xxx"
  }

  private fun maskIpv6(value: String): String? {
    if (!value.contains(':')) {
      return null
    }
    val segments = parseIpv6Segments(value) ?: return null
    return segments.dropLast(1).plus("***").joinToString(":")
  }

  private fun parseIpv6Segments(value: String): List<String>? {
    if (value.count { it == ':' } < 2) {
      return null
    }
    if (value.count { it == ':' } >= 2 && value.contains(":::")) {
      return null
    }

    val doubleColonIndex = value.indexOf("::")
    if (doubleColonIndex != -1 && value.indexOf("::", doubleColonIndex + 2) != -1) {
      return null
    }

    val headRaw = if (doubleColonIndex == -1) value else value.substring(0, doubleColonIndex)
    val tailRaw = if (doubleColonIndex == -1) "" else value.substring(doubleColonIndex + 2)
    val head = parseIpv6Section(headRaw) ?: return null
    val tail = parseIpv6Section(tailRaw) ?: return null

    val missingSegmentCount =
        if (doubleColonIndex == -1) {
          if (head.size != IPV6_SEGMENT_COUNT) {
            return null
          }
          0
        } else {
          IPV6_SEGMENT_COUNT - head.size - tail.size
        }
    if (missingSegmentCount < 1 && doubleColonIndex != -1) {
      return null
    }

    return buildList(IPV6_SEGMENT_COUNT) {
          addAll(head)
          repeat(max(missingSegmentCount, 0)) { add("0") }
          addAll(tail)
        }
        .takeIf { it.size == IPV6_SEGMENT_COUNT }
  }

  private fun parseIpv6Section(section: String): List<String>? {
    if (section.isEmpty()) {
      return emptyList()
    }
    val tokens = section.split(':')
    val segments = mutableListOf<String>()
    for ((index, token) in tokens.withIndex()) {
      if (token.isEmpty()) {
        return null
      }
      if ('.' in token) {
        if (index != tokens.lastIndex) {
          return null
        }
        segments += parseEmbeddedIpv4(token) ?: return null
        continue
      }
      segments +=
          token.toIntOrNull(radix = 16)?.takeIf { it in 0..0xFFFF }?.toString(16) ?: return null
    }
    return segments
  }

  private fun parseEmbeddedIpv4(value: String): List<String>? {
    val segments = value.split('.')
    if (segments.size != 4) {
      return null
    }
    val octets = mutableListOf<Int>()
    for (segment in segments) {
      val octet = segment.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
      octets += octet
    }
    return listOf(
        ((octets[0] shl 8) or octets[1]).toString(16),
        ((octets[2] shl 8) or octets[3]).toString(16),
    )
  }

  private companion object {
    const val IPV6_SEGMENT_COUNT = 8
  }
}
