package com.infosung.atomic.event.log.domain
/** Reserved payload for client application logs. */
data class ClientEventLogPayload(
    override val platform: EventLogPlatform,
    val appId: String,
    val appVersion: String,
    val userPseudoId: String,
    val sessionId: Long,
    val engagementTimeMsec: Long? = null,
    val screenName: String? = null,
    val releaseChannel: String? = null,
    val buildNumber: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val deviceCategory: String? = null,
    val deviceLanguage: String? = null,
    val operatingSystem: String? = null,
    val operatingSystemVersion: String? = null,
    val deviceModel: String? = null,
    val deviceBrand: String? = null,
    val browser: String? = null,
    val browserVersion: String? = null,
    val screenResolution: String? = null,
) : EventLogPlatformPayload {
  override fun toFields(): Map<String, EventLogValue> = buildMap {
    put("appId", EventLogValue.Text(appId))
    put("appVersion", EventLogValue.Text(appVersion))
    put("userPseudoId", EventLogValue.Text(userPseudoId))
    put("sessionId", EventLogValue.Integer(sessionId))
    engagementTimeMsec?.let { put("engagementTimeMsec", EventLogValue.Integer(it)) }
    screenName?.let { put("screenName", EventLogValue.Text(it)) }
    releaseChannel?.let { put("releaseChannel", EventLogValue.Text(it)) }
    buildNumber?.let { put("buildNumber", EventLogValue.Text(it)) }
    locale?.let { put("locale", EventLogValue.Text(it)) }
    timezone?.let { put("timezone", EventLogValue.Text(it)) }
    deviceCategory?.let { put("deviceCategory", EventLogValue.Text(it)) }
    deviceLanguage?.let { put("deviceLanguage", EventLogValue.Text(it)) }
    operatingSystem?.let { put("operatingSystem", EventLogValue.Text(it)) }
    operatingSystemVersion?.let { put("operatingSystemVersion", EventLogValue.Text(it)) }
    deviceModel?.let { put("deviceModel", EventLogValue.Text(it)) }
    deviceBrand?.let { put("deviceBrand", EventLogValue.Text(it)) }
    browser?.let { put("browser", EventLogValue.Text(it)) }
    browserVersion?.let { put("browserVersion", EventLogValue.Text(it)) }
    screenResolution?.let { put("screenResolution", EventLogValue.Text(it)) }
  }

  override fun validate(policy: EventLogPolicy): String? {
    if (!platform.isClientPlatform()) {
      return "client payload must use a client platform."
    }
    if (appId.isBlank() || appVersion.isBlank() || userPseudoId.isBlank()) {
      return "appId, appVersion, and userPseudoId must not be blank."
    }
    if (sessionId <= 0) {
      return "sessionId must be greater than zero."
    }
    if (engagementTimeMsec != null && engagementTimeMsec < 0) {
      return "engagementTimeMsec must be zero or greater."
    }
    return null
  }
}
