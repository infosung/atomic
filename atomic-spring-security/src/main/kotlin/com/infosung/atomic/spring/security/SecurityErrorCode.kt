package com.infosung.atomic.spring.security

enum class SecurityErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  SECURITY_UNAUTHORIZED(401, "Unauthorized"),
  SECURITY_FORBIDDEN(403, "Forbidden"),
}
