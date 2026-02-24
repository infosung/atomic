package com.infosung.atomic.spring.web.header

import com.infosung.atomic.contract.header.ApiHeaderNames
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

private val clientIpResolverLog = LoggerFactory.getLogger("ClientIpResolver")

fun HttpServletRequest.getClientIp(): String {
  for (header in ApiHeaderNames.CLIENT_IP_HEADER_KEYS) {
    val value = this.getHeader(header)
    if (value.isNullOrBlank()) continue
    val ip =
        value
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.lowercase() != "unknown" }

    if (!ip.isNullOrBlank()) {
      clientIpResolverLog.trace("Resolved client IP from header {}: {}", header, ip)
      return ip
    }
  }

  clientIpResolverLog.trace("Resolved client IP from remote address: {}", this.remoteAddr)
  return this.remoteAddr
}
