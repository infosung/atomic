package com.infosung.atomic.spring.web

import com.infosung.atomic.spring.web.exception.HttpRequestExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/** Rest client interceptor that logs request lifecycle and wraps execution failures. */
class RestClientInterceptor : ClientHttpRequestInterceptor {
  private val log: Logger = LoggerFactory.getLogger(RestClientInterceptor::class.java)

  /**
   * Executes outbound request and wraps unexpected failures into [HttpRequestExecutionException].
   */
  override fun intercept(
      request: HttpRequest,
      body: ByteArray,
      execution: ClientHttpRequestExecution,
  ): ClientHttpResponse {
    log.debug("Rest client request started: method={}, uri={}", request.method, request.uri)
    log.trace("Rest client request attributes={}, bodyBytes={}", request.attributes, body.size)
    return try {
      val response = execution.execute(request, body)
      log.debug(
          "Rest client request completed: method={}, uri={}, status={}, statusText={}",
          request.method,
          request.uri,
          response.statusCode,
          response.statusText,
      )
      response
    } catch (e: Exception) {
      log.error(
          "Rest client request failed: method={}, uri={}",
          request.method,
          request.uri,
          e,
      )
      throw HttpRequestExecutionException(
          method = request.method.name(),
          url = request.uri.toString(),
          cause = e,
      )
    }
  }
}
