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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.Instant

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
    val address = parseLiteralAddress(normalized) ?: return "***"
    return when (address) {
      is Inet4Address -> maskIpv4(address)
      is Inet6Address -> maskIpv6(address)
      else -> "***"
    }
  }

  private fun normalizeIpCandidate(value: String): String {
    val withoutScope = value.substringBefore('%')
    if (withoutScope.startsWith('[') && withoutScope.contains(']')) {
      return withoutScope.substringAfter('[').substringBefore(']')
    }
    return withoutScope
  }

  private fun parseLiteralAddress(value: String): InetAddress? =
      runCatching { InetAddress.ofLiteral(value) }.getOrNull()

  private fun maskIpv4(address: Inet4Address): String {
    val octets = address.address.map { it.toInt() and 0xFF }
    return "${octets[0]}.${octets[1]}.${octets[2]}.xxx"
  }

  private fun maskIpv6(address: Inet6Address): String {
    val hextets =
        address.address.asList().chunked(2).map {
          ((it[0].toInt() and 0xFF) shl 8 or (it[1].toInt() and 0xFF)).toString(16)
        }
    return hextets.take(4).plus(List(4) { MASKED_IPV6_SEGMENT }).joinToString(":")
  }

  private companion object {
    const val MASKED_IPV6_SEGMENT = "***"
  }
}
