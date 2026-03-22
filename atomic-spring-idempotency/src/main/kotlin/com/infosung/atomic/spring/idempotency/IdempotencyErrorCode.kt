package com.infosung.atomic.spring.idempotency

enum class IdempotencyErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency-Key header is required."),
  IDEMPOTENCY_REQUEST_PROCESSING(409, "Idempotent request is already processing."),
  IDEMPOTENCY_FINGERPRINT_MISMATCH(409, "Idempotency key has been used with a different request."),
  ;

  fun renderMessage(
      headerName: String,
  ): String {
    return when (this) {
      IDEMPOTENCY_KEY_REQUIRED -> "$headerName header is required."
      else -> defaultMessage
    }
  }
}
