package com.infosung.atomic.spring.web.header

import com.infosung.atomic.contract.header.TraceIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.springframework.mock.web.MockHttpServletRequest

class ApiHeaderMapperTest {
  private val traceIdGenerator = TraceIdGenerator()

  @AfterEach
  fun resetTraceIdGenerator() {
    traceIdGenerator.reset()
  }

  @Test
  fun `toHeaderDto should extract and normalize headers`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-Platform", "WEB")
          addHeader("X-Device-Id", "x-device-1")
          addHeader("X-App-Version", "1.0.0")
          addHeader("user-agent", "JUnit")
          addHeader("accept-language", "ko-KR")
          addHeader("X-Trace-Id", "trace-123")
          addHeader("X-Forwarded-For", "192.168.0.77, 10.0.0.1")
        }

    val dto = request.toHeaderDto()

    assertEquals("WEB", dto.platform)
    assertEquals("x-device-1", dto.deviceId)
    assertEquals("1.0.0", dto.appVersion)
    assertEquals("JUnit", dto.userAgent)
    assertEquals("ko-KR", dto.acceptLanguage)
    assertEquals("ko-KR", dto.customLang)
    assertEquals("trace-123", dto.traceId)
    assertEquals("192.168.0.0/24", dto.clientIp)
  }

  @Test
  fun `toHeaderDto should generate trace id when request header is missing`() {
    traceIdGenerator.configure { "generated-trace" }
    val request = MockHttpServletRequest("GET", "/v1/test")

    val dto = request.toHeaderDto(traceIdGenerator = traceIdGenerator)

    assertEquals("generated-trace", dto.traceId)
  }
}
