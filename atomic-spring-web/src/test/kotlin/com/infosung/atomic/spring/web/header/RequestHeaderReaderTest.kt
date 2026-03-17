package com.infosung.atomic.spring.web.header

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.mock.web.MockHttpServletRequest

class RequestHeaderReaderTest {
  @Test
  fun `RequestHeaderReader should read values from request`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-App-Version", "2.0.0")
          addHeader("X-Device-Id", "x-device-2")
          addHeader("X-Platform", "AOS")
          addHeader("X-Service-Name", "totp")
          addHeader("X-CUSTOM", "custom-value")
        }

    assertEquals("2.0.0", RequestHeaderReader.getAppVersion(request))
    assertEquals("x-device-2", RequestHeaderReader.getDeviceId(request))
    assertEquals("AOS", RequestHeaderReader.getPlatformString(request))
    assertEquals("totp", RequestHeaderReader.getServiceString(request))
    assertEquals("custom-value", RequestHeaderReader.getRequestHeader(request, "X-CUSTOM"))
    assertEquals(
        listOf("X-App-Version", "X-Device-Id", "X-Platform", "X-Service-Name", "X-CUSTOM"),
        RequestHeaderReader.getRequestHeaders(request).toList(),
    )
  }

  @Test
  fun `getDeviceId should ignore legacy DEVICE_ID header`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("DEVICE_ID", "legacy-device-3")
        }

    assertNull(RequestHeaderReader.getDeviceId(request))
  }

  @Test
  fun `getCustomLanguage should keep raw Accept-Language helper behavior`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8")
        }

    assertEquals(
        "ko-KR,ko;q=0.9,en-US;q=0.8",
        RequestHeaderReader.getCustomLanguage(request),
    )
  }

  @Test
  fun `getPreferredLanguageTag should delegate to parsed request language hint`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addPreferredLocale(Locale.forLanguageTag("pt-BR"))
        }

    assertEquals("pt-BR", RequestHeaderReader.getPreferredLanguageTag(request))
  }

  private fun java.util.Enumeration<String>.toList(): List<String> {
    val values = mutableListOf<String>()
    while (hasMoreElements()) {
      values.add(nextElement())
    }
    return values
  }
}
