package com.infosung.atomic.spring.idempotency

import jakarta.servlet.http.HttpServletRequest

/** Resolves request fingerprint used to detect key reuse with different requests. */
fun interface IdempotencyFingerprintResolver {
  fun resolve(request: HttpServletRequest): String
}
