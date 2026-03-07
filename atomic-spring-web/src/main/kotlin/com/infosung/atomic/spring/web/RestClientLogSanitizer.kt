package com.infosung.atomic.spring.web

import java.net.URI

/** Returns a log-safe URI string without query/fragment components. */
internal fun sanitizeUriForLog(uri: URI): String {
  val safeAuthority = uri.rawAuthority?.substringAfterLast('@')
  val sanitized =
      runCatching { URI(uri.scheme, safeAuthority, uri.rawPath, null, null) }.getOrNull()
  return when {
    sanitized != null && sanitized.toASCIIString().isNotBlank() -> sanitized.toASCIIString()
    !uri.rawPath.isNullOrBlank() -> uri.rawPath
    !uri.path.isNullOrBlank() -> uri.path
    else -> "/"
  }
}
