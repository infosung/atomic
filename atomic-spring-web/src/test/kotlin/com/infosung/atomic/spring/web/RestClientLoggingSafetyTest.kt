package com.infosung.atomic.spring.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.infosung.atomic.spring.web.exception.HttpRemoteCallException
import com.infosung.atomic.spring.web.exception.HttpRequestExecutionException
import java.io.IOException
import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse

class RestClientLoggingSafetyTest {
  @Test
  fun `sanitizeUriForLog should remove query and fragment`() {
    assertEquals(
        "https://api.example.com/v1/resource",
        sanitizeUriForLog(URI("https://api.example.com/v1/resource?token=abc#frag")),
    )
    assertEquals(
        "/v1/resource",
        sanitizeUriForLog(URI("/v1/resource?token=abc")),
    )
    assertEquals(
        "https://api.example.com/v1/resource",
        sanitizeUriForLog(URI("https://user:pass@api.example.com/v1/resource?token=abc")),
    )
  }

  @Test
  fun `RestClientErrorHandler should not log raw query or body`() {
    val handler = RestClientErrorHandler()
    val responseBody = """{"api_key":"super-secret","message":"failed"}"""
    val response = MockClientHttpResponse(responseBody.toByteArray(), HttpStatus.BAD_REQUEST)
    val url = URI("https://api.example.com/v1/callback?token=secretQuery")

    withListAppender(RestClientErrorHandler::class.java, Level.DEBUG) { events ->
      val exception =
          assertThrows<HttpRemoteCallException> { handler.handleError(url, HttpMethod.POST, response) }

      assertEquals("https://api.example.com/v1/callback?token=secretQuery", exception.url)
      assertEquals(responseBody, exception.responseBody)

      val logs = events.map { it.formattedMessage }
      assertTrue(logs.any { it.contains("bodyLength=") })
      assertFalse(logs.any { it.contains("bodySha256=") })
      assertFalse(logs.any { it.contains("token=secretQuery") })
      assertFalse(logs.any { it.contains("super-secret") })
      assertTrue(logs.any { it.contains("url=https://api.example.com/v1/callback") })
    }
  }

  @Test
  fun `RestClientErrorHandler should sanitize user info in logs`() {
    val handler = RestClientErrorHandler()
    val response = MockClientHttpResponse("""{"error":"failed"}""".toByteArray(), HttpStatus.BAD_REQUEST)
    val url = URI("https://user:pass@api.example.com/v1/callback?token=secretQuery")

    withListAppender(RestClientErrorHandler::class.java, Level.DEBUG) { events ->
      assertThrows<HttpRemoteCallException> { handler.handleError(url, HttpMethod.POST, response) }

      val logs = events.map { it.formattedMessage }
      assertFalse(logs.any { it.contains("user:pass") })
      assertFalse(logs.any { it.contains("token=secretQuery") })
      assertTrue(logs.any { it.contains("url=https://api.example.com/v1/callback") })
    }
  }

  @Test
  fun `RestClientInterceptor should log safe uri and attribute keys only`() {
    val interceptor = RestClientInterceptor()
    val request =
        MockClientHttpRequest(HttpMethod.GET, URI("https://api.example.com/v1/users?token=secretQuery"))
    request.attributes["trace"] = "super-secret-value"
    val execution = ClientHttpRequestExecution { _, _ ->
      MockClientHttpResponse(ByteArray(0), HttpStatus.OK)
    }

    withListAppender(RestClientInterceptor::class.java, Level.TRACE) { events ->
      interceptor.intercept(request, "payload".toByteArray(), execution)
      val logs = events.map { it.formattedMessage }
      assertTrue(logs.any { it.contains("uri=https://api.example.com/v1/users") })
      assertFalse(logs.any { it.contains("token=secretQuery") })
      assertFalse(logs.any { it.contains("super-secret-value") })
      assertTrue(logs.any { it.contains("attributeKeys=[trace]") })
    }
  }

  @Test
  fun `RestClientInterceptor failure should log safe uri and keep raw url in exception`() {
    val interceptor = RestClientInterceptor()
    val request =
        MockClientHttpRequest(
            HttpMethod.GET,
            URI("https://user:pass@api.example.com/v1/users?token=secretQuery"),
        )
    val execution = ClientHttpRequestExecution { _, _ -> throw IOException("downstream failed") }

    withListAppender(RestClientInterceptor::class.java, Level.DEBUG) { events ->
      val ex =
          assertThrows<HttpRequestExecutionException> {
            interceptor.intercept(request, ByteArray(0), execution)
          }

      assertEquals("https://user:pass@api.example.com/v1/users?token=secretQuery", ex.url)
      val logs = events.map { it.formattedMessage }
      assertFalse(logs.any { it.contains("user:pass") })
      assertFalse(logs.any { it.contains("token=secretQuery") })
      assertTrue(logs.any { it.contains("uri=https://api.example.com/v1/users") })
    }
  }

  @Test
  fun `RestClientInterceptor should not leak raw uri contained in exception message`() {
    val interceptor = RestClientInterceptor()
    val request =
        MockClientHttpRequest(
            HttpMethod.GET,
            URI("https://api.example.com/v1/users?token=secretQuery"),
        )
    val leakingMessage =
        "I/O error on GET request for \"https://api.example.com/v1/users?token=secretQuery\""
    val execution = ClientHttpRequestExecution { _, _ -> throw IOException(leakingMessage) }

    withListAppender(RestClientInterceptor::class.java, Level.DEBUG) { events ->
      assertThrows<HttpRequestExecutionException> { interceptor.intercept(request, ByteArray(0), execution) }
      val logs = events.map { it.formattedMessage }
      assertFalse(logs.any { it.contains("token=secretQuery") })
      assertTrue(logs.any { it.contains("errorType=IOException") })
      assertTrue(logs.any { it.contains("uri=https://api.example.com/v1/users") })
    }
  }

  @Test
  fun `RestClientErrorHandler should log metadata on 5xx without raw body`() {
    val handler = RestClientErrorHandler()
    val responseBody = """{"secret":"server-token","reason":"failed"}"""
    val response = MockClientHttpResponse(responseBody.toByteArray(), HttpStatus.INTERNAL_SERVER_ERROR)
    val url = URI("https://api.example.com/v1/server?token=secretQuery")

    withListAppender(RestClientErrorHandler::class.java, Level.DEBUG) { events ->
      assertThrows<HttpRemoteCallException> { handler.handleError(url, HttpMethod.GET, response) }
      val logs = events.map { it.formattedMessage }
      assertTrue(logs.any { it.contains("status=500") })
      assertTrue(logs.any { it.contains("bodyLength=") })
      assertFalse(logs.any { it.contains("server-token") })
      assertFalse(logs.any { it.contains("token=secretQuery") })
    }
  }

  private fun withListAppender(
      loggerClass: Class<*>,
      level: Level,
      block: (events: List<ILoggingEvent>) -> Unit,
  ) {
    val logger = LoggerFactory.getLogger(loggerClass) as Logger
    val previousLevel = logger.level
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)
    logger.level = level
    try {
      block(appender.list)
    } finally {
      logger.detachAppender(appender)
      logger.level = previousLevel
    }
  }
}
