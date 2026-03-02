package com.infosung.atomic.contract.header

/**
 * Standard HTTP header names used across Atomic modules.
 */
object ApiHeaderNames {
  const val HEADER_X_DEVICE_ID = "X-Device-Id"
  const val HEADER_X_APP_VERSION = "X-App-Version"
  const val HEADER_X_PLATFORM = "X-Platform"
  const val HEADER_X_SERVICE_NAME = "X-Service-Name"
  const val HEADER_X_TRACE_ID = "X-Trace-Id"
  const val HEADER_X_CUSTOM_LANGUAGE = "X-Custom-Language"

  const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
  const val HEADER_USER_AGENT = "User-Agent"

  val CLIENT_IP_HEADER_KEYS: List<String> =
      listOf(
          "X-Forwarded-For",
          "Proxy-Client-IP",
          "WL-Proxy-Client-IP",
          "HTTP_X_FORWARDED_FOR",
          "HTTP_X_FORWARDED",
          "HTTP_X_CLUSTER_CLIENT_IP",
          "HTTP_CLIENT_IP",
          "HTTP_FORWARDED_FOR",
          "HTTP_FORWARDED",
          "HTTP_VIA",
          "REMOTE_ADDR",
          "CF-Connecting-IP",
          "X-Real-IP",
      )
}
