package com.infosung.atomic.event.log.domain
/** Reserved payload for WebSocket logs. */
data class WebSocketEventLogPayload(
    val channel: String,
    val direction: String,
    val messageType: String? = null,
    val connectionId: String,
    val sessionId: String? = null,
    val closeCode: Int? = null,
    val clientIpMasked: String? = null,
) : EventLogPlatformPayload {
  override val platform: EventLogPlatform = EventLogPlatform.WEBSOCKET

  override fun toFields(): Map<String, EventLogValue> = buildMap {
    put("channel", EventLogValue.Text(channel))
    put("direction", EventLogValue.Text(direction))
    put("connectionId", EventLogValue.Text(connectionId))
    messageType?.let { put("messageType", EventLogValue.Text(it)) }
    sessionId?.let { put("sessionId", EventLogValue.Text(it)) }
    closeCode?.let { put("closeCode", EventLogValue.Integer(it)) }
    clientIpMasked?.let { put("clientIpMasked", EventLogValue.Text(it)) }
  }

  override fun validate(policy: EventLogPolicy): String? {
    if (channel.isBlank()) {
      return "channel must not be blank."
    }
    if (direction.isBlank()) {
      return "direction must not be blank."
    }
    if (connectionId.isBlank()) {
      return "connectionId must not be blank."
    }
    return null
  }
}
