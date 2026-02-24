package com.infosung.atomic.spring.web.log

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.exception.HttpFilterProcessingException
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory

object ApiLogContext {
  private const val API_LOG_CONTEXT_KEY = "baseapi.api.log.context"

  fun set(
      request: HttpServletRequest,
      data: ServiceLog,
  ) {
    request.setAttribute(API_LOG_CONTEXT_KEY, data)
  }

  fun remove(request: HttpServletRequest) {
    request.removeAttribute(API_LOG_CONTEXT_KEY)
  }

  fun get(request: HttpServletRequest): ServiceLog? {
    val data = request.getAttribute(API_LOG_CONTEXT_KEY)
    return data as? ServiceLog
  }
}

class ApiLogFilter(
    private val logger: ServiceLogger,
    private val timeProvider: TimeProvider = TimeProvider(),
) : Filter {
  private val log = LoggerFactory.getLogger(ApiLogFilter::class.java)

  override fun doFilter(
      p0: ServletRequest,
      p1: ServletResponse,
      p2: FilterChain,
  ) {
    val response = p1 as? HttpServletResponse
    var errorStatus: Int? = null
    val request = p0 as? HttpServletRequest
    log.debug(
        "ApiLogFilter started: method={}, uri={}",
        request?.method,
        request?.requestURI,
    )
    try {
      p2.doFilter(p0, p1)
    } catch (e: Throwable) {
      val currentStatus = response?.status ?: HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      errorStatus =
          if (currentStatus >= HttpServletResponse.SC_BAD_REQUEST) {
            currentStatus
          } else {
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
          }
      when (e) {
        is Error -> throw e
        is HttpStatusException -> throw e
        else ->
            throw HttpFilterProcessingException(
                method = request?.method,
                uri = request?.requestURI,
                status = errorStatus,
                cause = e,
            )
      }
    } finally {
      val statusCode =
          errorStatus ?: response?.status ?: HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      logging(p0, statusCode)
      log.debug(
          "ApiLogFilter finished: method={}, uri={}, status={}",
          request?.method,
          request?.requestURI,
          statusCode,
      )
    }
  }

  fun logging(
      request: ServletRequest,
      status: Int,
  ) {
    val httpRequest = request as? HttpServletRequest ?: return
    ApiLogContext.get(httpRequest)?.let { data ->
      log.trace("ApiLogFilter context found: traceId={}", data.traceId)
      val now = timeProvider.nowMillis()
      val requestLog = data as? ServiceApiRequestLog
      logger.logging(
          ServiceApiResponseLog(
              traceId = data.traceId,
              logTime = now,
              httpMethod = requestLog?.httpMethod ?: "",
              executeTime = now - data.logTime,
              status = status,
              endPoint = requestLog?.endPoint ?: "",
              userId = requestLog?.userId,
              deviceId = requestLog?.deviceId,
              clientIp = requestLog?.clientIp,
          ),
      )
    } ?: run { log.trace("ApiLogFilter context not found for request: {}", httpRequest.requestURI) }
    ApiLogContext.remove(httpRequest)
  }
}
