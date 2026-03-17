package com.infosung.atomic.spring.web.header

import com.infosung.atomic.contract.header.ApiHeaderNames
import jakarta.servlet.http.HttpServletRequest
import java.util.Enumeration

/** Helper for reading common API headers from [HttpServletRequest]. */
object RequestHeaderReader {
  /** Returns all header names. */
  fun getRequestHeaders(request: HttpServletRequest): Enumeration<String> = request.headerNames

  /** Returns header value for [key]. */
  fun getRequestHeader(request: HttpServletRequest, key: String): String? = request.getHeader(key)

  fun getAppVersion(request: HttpServletRequest): String? =
      getFirstHeader(request, ApiHeaderNames.HEADER_X_APP_VERSION)

  fun getDeviceId(request: HttpServletRequest): String? =
      request.getHeader(ApiHeaderNames.HEADER_X_DEVICE_ID)?.takeIf { it.isNotBlank() }

  fun getPlatformString(request: HttpServletRequest): String? =
      getFirstHeader(request, ApiHeaderNames.HEADER_X_PLATFORM)

  fun getServiceString(request: HttpServletRequest): String? =
      getFirstHeader(request, ApiHeaderNames.HEADER_X_SERVICE_NAME)

  fun getTraceId(request: HttpServletRequest): String? =
      getFirstHeader(request, ApiHeaderNames.HEADER_X_TRACE_ID)

  /** Returns raw language header helper (`X-Custom-Language` first, else raw `Accept-Language`). */
  fun getCustomLanguage(request: HttpServletRequest): String? =
      getFirstHeader(
          request,
          ApiHeaderNames.HEADER_X_CUSTOM_LANGUAGE,
          ApiHeaderNames.HEADER_ACCEPT_LANGUAGE,
      )

  /** Returns parsed request language hint (`X-Custom-Language` first, else first Servlet-preferred locale tag). */
  fun getPreferredLanguageTag(request: HttpServletRequest): String? =
      RequestLanguageResolver.resolvePreferredLanguageTag(request)

  private fun getFirstHeader(request: HttpServletRequest, vararg names: String): String? {
    for (name in names) {
      val value = request.getHeader(name)?.takeIf { it.isNotBlank() }
      if (value != null) return value
    }
    return null
  }
}
