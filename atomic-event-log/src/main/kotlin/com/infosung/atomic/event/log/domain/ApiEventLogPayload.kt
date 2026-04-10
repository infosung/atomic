package com.infosung.atomic.event.log.domain
/** Reserved payload for HTTP API logs. */
data class ApiEventLogPayload(
    val httpMethod: String,
    val endpoint: String,
    val status: Int? = null,
    val executeTimeMs: Long? = null,
    val deviceId: String? = null,
    val clientIpMasked: String? = null,
    val customLang: String? = null,
    val acceptLanguage: String? = null,
    val query: String? = null,
    val body: String? = null,
) : EventLogPlatformPayload {
  override val platform: EventLogPlatform = EventLogPlatform.API

  override fun toFields(): Map<String, EventLogValue> = buildMap {
    put("httpMethod", EventLogValue.Text(httpMethod))
    put("endpoint", EventLogValue.Text(endpoint))
    status?.let { put("status", EventLogValue.Integer(it)) }
    executeTimeMs?.let { put("executeTimeMs", EventLogValue.Integer(it)) }
    deviceId?.let { put("deviceId", EventLogValue.Text(it)) }
    clientIpMasked?.let { put("clientIpMasked", EventLogValue.Text(it)) }
    customLang?.let { put("customLang", EventLogValue.Text(it)) }
    acceptLanguage?.let { put("acceptLanguage", EventLogValue.Text(it)) }
    query?.let { put("query", EventLogValue.Text(it)) }
    body?.let { put("body", EventLogValue.Text(it)) }
  }

  override fun validate(policy: EventLogPolicy): String? {
    if (httpMethod.isBlank()) {
      return "httpMethod must not be blank."
    }
    if (endpoint.isBlank()) {
      return "endpoint must not be blank."
    }
    return null
  }
}
