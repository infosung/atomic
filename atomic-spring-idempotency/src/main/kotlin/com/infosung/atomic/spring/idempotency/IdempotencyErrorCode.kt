package com.infosung.atomic.spring.idempotency

enum class IdempotencyErrorCode {
  IDEMPOTENCY_KEY_REQUIRED,
  IDEMPOTENCY_REQUEST_PROCESSING,
  IDEMPOTENCY_FINGERPRINT_MISMATCH,
}
