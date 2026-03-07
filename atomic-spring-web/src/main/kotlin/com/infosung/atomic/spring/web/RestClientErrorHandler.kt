package com.infosung.atomic.spring.web

import com.infosung.atomic.spring.web.exception.HttpRemoteCallException
import java.net.URI
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler

/** Spring [ResponseErrorHandler] that converts 4xx/5xx responses into [HttpRemoteCallException]. */
class RestClientErrorHandler : ResponseErrorHandler {
  private val log = LoggerFactory.getLogger(RestClientErrorHandler::class.java)

  /** Returns true for 4xx/5xx responses. */
  override fun hasError(response: ClientHttpResponse): Boolean {
    val hasError = response.statusCode.is4xxClientError || response.statusCode.is5xxServerError
    log.trace("Rest client response hasError={}, status={}", hasError, response.statusCode)
    return hasError
  }

  /** Throws [HttpRemoteCallException] with status/method/url/responseBody. */
  override fun handleError(
      url: URI,
      method: HttpMethod,
      response: ClientHttpResponse,
  ) {
    val body = response.body.bufferedReader().use { it.readText() }
    val safeUrl = sanitizeUriForLog(url)
    val bodyLength = body.length
    log.debug(
        "Handling rest client error response: method={}, url={}, status={}",
        method,
        safeUrl,
        response.statusCode,
    )
    if (response.statusCode.is4xxClientError) {
      log.warn(
          "Rest client 4xx error: method={}, url={}, status={}, bodyLength={}",
          method,
          safeUrl,
          response.statusCode,
          bodyLength,
      )
      throw HttpRemoteCallException(
          status = response.statusCode.value(),
          method = method.name(),
          url = url.toString(),
          responseBody = body,
      )
    } else if (response.statusCode.is5xxServerError) {
      log.error(
          "Rest client 5xx error: method={}, url={}, status={}, bodyLength={}",
          method,
          safeUrl,
          response.statusCode,
          bodyLength,
      )
      throw HttpRemoteCallException(
          status = response.statusCode.value(),
          method = method.name(),
          url = url.toString(),
          responseBody = body,
      )
    }

    super.handleError(url, method, response)
  }
}
