package com.infosung.atomic.spring.web.header

import com.infosung.atomic.contract.header.ApiHeaderDto
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.header.TraceIdGenerator
import com.infosung.atomic.contract.security.IpMasker
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

private val apiHeaderMapperLog = LoggerFactory.getLogger("ApiHeaderMapper")

/**
 * Maps servlet request into normalized [ApiHeaderDto].
 *
 * Behavior:
 * - Uses inbound trace-id when present.
 * - Generates trace-id via [traceIdGenerator] when missing.
 * - Masks resolved client IP for safer logging/storage.
 */
fun HttpServletRequest.toHeaderDto(
    traceIdGenerator: TraceIdGenerator = TraceIdGenerator(),
): ApiHeaderDto {
  val clientIp = getClientIp()
  val traceId = RequestHeaderReader.getTraceId(this) ?: traceIdGenerator.generate()
  apiHeaderMapperLog.trace(
      "Building ApiHeaderDto: method={}, uri={}, traceId={}",
      this.method,
      this.requestURI,
      traceId,
  )
  return ApiHeaderDto(
      platform = RequestHeaderReader.getPlatformString(this),
      deviceId = RequestHeaderReader.getDeviceId(this),
      appVersion = RequestHeaderReader.getAppVersion(this),
      userAgent = this.getHeader(ApiHeaderNames.HEADER_USER_AGENT),
      clientIp = IpMasker.mask(clientIp),
      customLang = RequestHeaderReader.getCustomLanguage(this),
      acceptLanguage = this.getHeader(ApiHeaderNames.HEADER_ACCEPT_LANGUAGE),
      traceId = traceId,
  )
}
