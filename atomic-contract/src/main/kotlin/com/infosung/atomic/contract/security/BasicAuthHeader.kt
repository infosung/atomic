package com.infosung.atomic.contract.security

import java.util.Base64

object BasicAuthHeader {
  fun create(
      username: String,
      password: String,
  ): String = "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
}
