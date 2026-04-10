package com.infosung.atomic.event.log

import com.infosung.atomic.event.log.domain.ClientEventLogPayload
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogPolicy
import com.infosung.atomic.event.log.domain.EventLogValue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ClientEventLogPayloadTest {
  @Test
  fun `toFields exposes ga compatible reserved client fields`() {
    val payload =
        ClientEventLogPayload(
            platform = EventLogPlatform.CLIENT_DESKTOP,
            appId = "fillingheart.windows",
            appVersion = "1.2.3",
            userPseudoId = "pseudo-1",
            sessionId = 1712744100L,
            engagementTimeMsec = 5_000L,
            screenName = "home",
            releaseChannel = "stable",
            buildNumber = "1203",
            locale = "ko-KR",
            timezone = "Asia/Seoul",
            deviceCategory = "desktop",
            deviceLanguage = "ko",
            operatingSystem = "Windows",
            operatingSystemVersion = "11",
            deviceModel = "Surface",
            deviceBrand = "Microsoft",
            browser = "Edge",
            browserVersion = "135.0",
            screenResolution = "1920x1080",
        )

    val fields = payload.toFields()

    assertEquals("pseudo-1", textValue(fields.getValue("userPseudoId")))
    assertEquals(1712744100L, integerValue(fields.getValue("sessionId")))
    assertEquals(5_000L, integerValue(fields.getValue("engagementTimeMsec")))
    assertEquals("home", textValue(fields.getValue("screenName")))
    assertEquals("desktop", textValue(fields.getValue("deviceCategory")))
    assertEquals("Windows", textValue(fields.getValue("operatingSystem")))
    assertEquals("1920x1080", textValue(fields.getValue("screenResolution")))
  }

  @Test
  fun `validate rejects blank user pseudo id and non positive session id`() {
    val payload =
        ClientEventLogPayload(
            platform = EventLogPlatform.CLIENT_DESKTOP,
            appId = "fillingheart.windows",
            appVersion = "1.2.3",
            userPseudoId = "",
            sessionId = 0L,
        )

    val validation = payload.validate(EventLogPolicy())

    assertTrue(validation != null)
    assertTrue(validation.contains("userPseudoId"))
  }

  @Test
  fun `validate accepts ga compatible client payload`() {
    val payload =
        ClientEventLogPayload(
            platform = EventLogPlatform.CLIENT_WEB,
            appId = "fillingheart.web",
            appVersion = "2.0.0",
            userPseudoId = "pseudo-1",
            sessionId = 1712744100L,
            engagementTimeMsec = 250L,
            screenName = "landing",
            deviceCategory = "desktop",
            operatingSystem = "macOS",
            operatingSystemVersion = "14.4",
        )

    assertNull(payload.validate(EventLogPolicy()))
  }

  private fun textValue(value: EventLogValue): String = (value as EventLogValue.Text).value

  private fun integerValue(value: EventLogValue): Long = (value as EventLogValue.Integer).value
}
