package com.infosung.atomic.spring.idempotency

import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryIdempotencyStoreTest {
  @Test
  fun `claim should return completed after complete`() {
    val store = InMemoryIdempotencyStore()
    val key = "POST|/api/v1/orders|abc"
    val fingerprint = "fp-1"
    val expiresAt = System.currentTimeMillis() + 60_000

    val claimed = store.claim(key = key, fingerprint = fingerprint, expiresAtMillis = expiresAt)
    val claimToken = (claimed as IdempotencyClaimResult.Claimed).claimToken
    store.complete(
        key = key,
        claimToken = claimToken,
        fingerprint = fingerprint,
        response =
            IdempotencyStoredResponse(
                status = 201,
                contentType = "application/json",
                headers = mapOf("X-Test" to listOf("yes")),
                body = """{"ok":true}""".toByteArray(),
            ),
        expiresAtMillis = expiresAt,
    )
    val replay = store.claim(key = key, fingerprint = fingerprint, expiresAtMillis = expiresAt)

    assertTrue(replay is IdempotencyClaimResult.Completed)
    replay as IdempotencyClaimResult.Completed
    assertEquals(201, replay.response.status)
  }

  @Test
  fun `claim should reject fingerprint mismatch for same key`() {
    val store = InMemoryIdempotencyStore()
    val key = "POST|/api/v1/orders|abc"
    val expiresAt = System.currentTimeMillis() + 60_000
    store.claim(key = key, fingerprint = "fp-1", expiresAtMillis = expiresAt)

    val mismatch = store.claim(key = key, fingerprint = "fp-2", expiresAtMillis = expiresAt)

    assertTrue(mismatch is IdempotencyClaimResult.FingerprintMismatch)
  }

  @Test
  fun `store should honor injected now provider for expiry checks`() {
    val now = AtomicLong(1_000L)
    val store = InMemoryIdempotencyStore(nowProvider = now::get)
    val key = "POST|/api/v1/orders|abc"
    val expiresAt = now.get() + 100

    val first = store.claim(key = key, fingerprint = "fp-1", expiresAtMillis = expiresAt)
    now.set(1_200L)
    val second = store.claim(key = key, fingerprint = "fp-1", expiresAtMillis = now.get() + 100)

    assertTrue(first is IdempotencyClaimResult.Claimed)
    assertTrue(second is IdempotencyClaimResult.Claimed)
  }
}
