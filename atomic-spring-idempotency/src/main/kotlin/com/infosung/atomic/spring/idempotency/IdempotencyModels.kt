package com.infosung.atomic.spring.idempotency

/** Cached HTTP response for idempotent replay. */
data class IdempotencyStoredResponse(
    val status: Int,
    val contentType: String?,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val bodyOmittedForSizeLimit: Boolean = false,
)

/** Claim result for one idempotency-key request. */
sealed interface IdempotencyClaimResult {
  data class Claimed(
      val claimToken: String,
  ) : IdempotencyClaimResult

  data class Completed(
      val response: IdempotencyStoredResponse,
  ) : IdempotencyClaimResult

  data object Processing : IdempotencyClaimResult

  data object FingerprintMismatch : IdempotencyClaimResult
}
