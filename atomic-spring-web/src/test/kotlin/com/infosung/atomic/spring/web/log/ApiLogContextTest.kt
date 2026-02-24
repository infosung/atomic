package com.infosung.atomic.spring.web.log

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.exception.HttpFilterProcessingException
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class ApiLogContextTest {
  private val timeProvider = TimeProvider()

  @AfterEach
  fun resetTimeProvider() {
    timeProvider.reset()
  }

  @Test
  fun `context should be stored and removed by request attribute`() {
    val request = MockHttpServletRequest()
    val data =
        ServiceApiRequestLog(
            traceId = "trace-1",
            logTime = 1_000L,
            httpMethod = "GET",
            endPoint = "/v1/test",
        )

    ApiLogContext.set(request, data)
    assertEquals(data, ApiLogContext.get(request))

    ApiLogContext.remove(request)
    assertNull(ApiLogContext.get(request))
  }

  @Test
  fun `filter should consume context and emit response log`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val saver = InMemoryLogSaver()
    val logger = ServiceLogger(saver)
    val filter = ApiLogFilter(logger, timeProvider)
    val request = MockHttpServletRequest("GET", "/v1/test")
    val response = MockHttpServletResponse()

    ApiLogContext.set(
        request,
        ServiceApiRequestLog(
            traceId = "trace-2",
            logTime = now.toEpochMilli() - 5,
            httpMethod = "GET",
            endPoint = "/v1/test",
        ),
    )

    filter.doFilter(request, response, MockFilterChain())
    logger.send()

    assertNull(ApiLogContext.get(request))
    assertEquals(1, saver.logs.size)
    val responseLog = saver.logs.first() as ServiceApiResponseLog
    assertEquals(200, responseLog.status)
    assertEquals(5L, responseLog.executeTime)
    assertEquals(now.toEpochMilli(), responseLog.logTime)
  }

  @Test
  fun `filter should emit response log with 500 when exception is thrown`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val saver = InMemoryLogSaver()
    val logger = ServiceLogger(saver)
    val filter = ApiLogFilter(logger, timeProvider)
    val request = MockHttpServletRequest("GET", "/v1/fail")
    val response = MockHttpServletResponse()

    ApiLogContext.set(
        request,
        ServiceApiRequestLog(
            traceId = "trace-3",
            logTime = now.toEpochMilli() - 5,
            httpMethod = "GET",
            endPoint = "/v1/fail",
        ),
    )

    val exception =
        assertThrows(HttpFilterProcessingException::class.java) {
          filter.doFilter(
              request,
              response,
              object : FilterChain {
                override fun doFilter(
                    request: ServletRequest?,
                    response: ServletResponse?,
                ): Unit = throw ServletException("boom")
              },
          )
        }
    assertEquals(500, exception.status)
    assertEquals(ServletException::class.java, exception.cause?.javaClass)
    logger.send()

    val responseLog = saver.logs.filterIsInstance<ServiceApiResponseLog>().single()
    assertEquals(500, responseLog.status)
    assertEquals(5L, responseLog.executeTime)
    assertEquals(now.toEpochMilli(), responseLog.logTime)
    assertNull(ApiLogContext.get(request))
  }

  private class InMemoryLogSaver : LogSaver {
    val logs = mutableListOf<ServiceLog>()

    override fun saveAll(logs: List<ServiceLog>) {
      this.logs.addAll(logs)
    }
  }
}
