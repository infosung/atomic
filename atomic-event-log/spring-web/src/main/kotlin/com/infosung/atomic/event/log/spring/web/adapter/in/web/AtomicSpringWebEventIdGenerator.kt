package com.infosung.atomic.event.log.spring.web.adapter.`in`.web

import com.infosung.atomic.spring.web.log.ServiceLog
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Stable event-id generator for spring-web request/response logs. */
fun interface AtomicSpringWebEventIdGenerator {
  fun generate(
      serviceId: String,
      logEntry: ServiceLog,
      eventName: String,
  ): String
}

/** Default deterministic event-id generator used by the spring-web adapter. */
class DefaultAtomicSpringWebEventIdGenerator : AtomicSpringWebEventIdGenerator {
  override fun generate(
      serviceId: String,
      logEntry: ServiceLog,
      eventName: String,
  ): String {
    val token =
        "$serviceId|$eventName|${logEntry.traceId}|${logEntry.logTime}|${logEntry::class.java.name}"
    return UUID.nameUUIDFromBytes(token.toByteArray(StandardCharsets.UTF_8)).toString()
  }
}
