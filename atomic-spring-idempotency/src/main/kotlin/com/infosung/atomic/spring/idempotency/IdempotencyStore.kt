package com.infosung.atomic.spring.idempotency

/** Storage contract for HTTP idempotency flow. */
interface IdempotencyStore {
  fun claim(
      key: String,
      fingerprint: String,
      expiresAtMillis: Long,
  ): IdempotencyClaimResult

  fun complete(
      key: String,
      claimToken: String,
      fingerprint: String,
      response: IdempotencyStoredResponse,
      expiresAtMillis: Long,
  )

  fun remove(
      key: String,
      claimToken: String,
  )
}
