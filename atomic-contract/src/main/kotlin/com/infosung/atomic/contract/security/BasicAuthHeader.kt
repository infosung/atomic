package com.infosung.atomic.contract.security

import java.util.Base64

/**
 * Utility for creating RFC 7617 Basic Authorization header values.
 */
object BasicAuthHeader {
  /**
   * Builds `Authorization` header value for Basic auth.
   */
  fun create(
      username: String,
      password: String,
  ): String = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}
