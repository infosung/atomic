package com.infosung.atomic.event.log.domain
/** Reserved payload for server-side logs. */
data class ServerEventLogPayload(
    val hostName: String,
    val instanceId: String,
    val loggerName: String,
    val level: String,
    val message: String,
    val threadName: String? = null,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
) : EventLogPlatformPayload {
  override val platform: EventLogPlatform = EventLogPlatform.SERVER

  override fun toFields(): Map<String, EventLogValue> = buildMap {
    put("hostName", EventLogValue.Text(hostName))
    put("instanceId", EventLogValue.Text(instanceId))
    put("loggerName", EventLogValue.Text(loggerName))
    put("level", EventLogValue.Text(level))
    put("message", EventLogValue.Text(message))
    threadName?.let { put("threadName", EventLogValue.Text(it)) }
    exceptionClass?.let { put("exceptionClass", EventLogValue.Text(it)) }
    exceptionMessage?.let { put("exceptionMessage", EventLogValue.Text(it)) }
  }

  override fun validate(policy: EventLogPolicy): String? {
    if (hostName.isBlank() || instanceId.isBlank() || loggerName.isBlank() || level.isBlank()) {
      return "hostName, instanceId, loggerName, and level must not be blank."
    }
    if (message.isBlank()) {
      return "message must not be blank."
    }
    return null
  }
}
