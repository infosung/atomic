package com.infosung.atomic.spring.idempotency

import jakarta.servlet.http.HttpServletRequest

/**
 * Default HTTP fingerprint.
 *
 * Does not include raw request body. Override with custom resolver when body-sensitive matching is
 * required.
 */
class DefaultIdempotencyFingerprintResolver : IdempotencyFingerprintResolver {
  override fun resolve(request: HttpServletRequest): String {
    val method = request.method ?: ""
    val uri = request.requestURI ?: ""
    val query = request.queryString ?: ""
    val contentType = request.contentType ?: ""
    val contentLength = request.contentLengthLong.takeIf { it >= 0 } ?: -1
    val principal = request.userPrincipal?.name ?: ""
    return "$method|$uri|$query|$contentType|$contentLength|$principal"
  }
}
