package com.infosung.atomic.spring.idempotency

import com.infosung.atomic.contract.time.TimeProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper

class IdempotencyFilterTest {
  private val objectMapper = ObjectMapper()

  @Test
  fun `filter should replay stored response for duplicate key`() {
    val store = InMemoryIdempotencyStore()
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            timeProvider = TimeProvider(),
            includeMethods = setOf("POST"),
        )
    val chainCalls = AtomicInteger(0)
    val chain = FilterChain { _, response ->
      chainCalls.incrementAndGet()
      val http = response as HttpServletResponse
      http.status = 201
      http.contentType = "application/json"
      http.writer.write("""{"created":true}""")
    }

    val firstRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    firstRequest.addHeader("Idempotency-Key", "abc-1")
    val firstResponse = MockHttpServletResponse()
    filter.doFilter(firstRequest, firstResponse, chain)

    val secondRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    secondRequest.addHeader("Idempotency-Key", "abc-1")
    val secondResponse = MockHttpServletResponse()
    filter.doFilter(secondRequest, secondResponse, chain)

    assertEquals(1, chainCalls.get())
    assertEquals(201, secondResponse.status)
    assertEquals("""{"created":true}""", secondResponse.contentAsString)
    assertEquals("true", secondResponse.getHeader("X-Idempotent-Replay"))
  }

  @Test
  fun `filter should return 400 when idempotency key is missing and required`() {
    val filter =
        IdempotencyFilter(
            store = InMemoryIdempotencyStore(),
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            requireHeader = true,
            includeMethods = setOf("POST"),
        )
    val response = MockHttpServletResponse()
    val request = MockHttpServletRequest("POST", "/api/v1/orders")
    val chain = FilterChain { _, _ -> throw IllegalStateException("chain should not execute") }

    filter.doFilter(request, response, chain)

    assertEquals(400, response.status)
    assertEquals("application/json", response.contentType)
    val body = objectMapper.readTree(response.contentAsString)
    assertEquals("IDEMPOTENCY_KEY_REQUIRED", body["code"]?.stringValue())
    assertEquals("Idempotency-Key header is required.", body["message"]?.stringValue())
  }

  @Test
  fun `filter should return 409 when key is reused with different fingerprint`() {
    val store = InMemoryIdempotencyStore()
    val resolver = IdempotencyFingerprintResolver { request ->
      request.getHeader("X-Fingerprint") ?: "default"
    }
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = resolver,
            includeMethods = setOf("POST"),
        )
    val chain = FilterChain { _, response -> (response as HttpServletResponse).status = 200 }

    val first = MockHttpServletRequest("POST", "/api/v1/orders")
    first.addHeader("Idempotency-Key", "abc-2")
    first.addHeader("X-Fingerprint", "one")
    filter.doFilter(first, MockHttpServletResponse(), chain)

    val second = MockHttpServletRequest("POST", "/api/v1/orders")
    second.addHeader("Idempotency-Key", "abc-2")
    second.addHeader("X-Fingerprint", "two")
    val secondResponse = MockHttpServletResponse()
    filter.doFilter(second, secondResponse, chain)

    assertEquals(409, secondResponse.status)
    assertEquals("application/json", secondResponse.contentType)
    val body = objectMapper.readTree(secondResponse.contentAsString)
    assertEquals("IDEMPOTENCY_FINGERPRINT_MISMATCH", body["code"]?.stringValue())
    assertEquals(
        "Idempotency key has been used with a different request.",
        body["message"]?.stringValue(),
    )
  }

  @Test
  fun `idempotency error codes should remain stable`() {
    assertEquals(
        listOf(
            "IDEMPOTENCY_KEY_REQUIRED",
            "IDEMPOTENCY_REQUEST_PROCESSING",
            "IDEMPOTENCY_FINGERPRINT_MISMATCH",
        ),
        IdempotencyErrorCode.entries.map { it.name },
    )
  }

  @Test
  fun `filter should return stable code while request is already processing`() {
    val filter =
        IdempotencyFilter(
            store =
                object : IdempotencyStore {
                  override fun claim(
                      key: String,
                      fingerprint: String,
                      expiresAtMillis: Long,
                  ): IdempotencyClaimResult = IdempotencyClaimResult.Processing

                  override fun complete(
                      key: String,
                      claimToken: String,
                      fingerprint: String,
                      response: IdempotencyStoredResponse,
                      expiresAtMillis: Long,
                  ) = Unit

                  override fun remove(
                      key: String,
                      claimToken: String,
                  ) = Unit
                },
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
            failOpen = false,
        )
    val request =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "processing-key")
        }
    val response = MockHttpServletResponse()
    val chain = FilterChain { _, _ -> throw IllegalStateException("chain should not execute") }

    filter.doFilter(request, response, chain)

    assertEquals(409, response.status)
    assertEquals("application/json", response.contentType)
    val body = objectMapper.readTree(response.contentAsString)
    assertEquals("IDEMPOTENCY_REQUEST_PROCESSING", body["code"]?.stringValue())
    assertEquals("Idempotent request is already processing.", body["message"]?.stringValue())
  }

  @Test
  fun `filter should keep success response when complete fails and failOpen is true`() {
    val removeCalls = AtomicInteger(0)
    val store =
        object : IdempotencyStore {
          override fun claim(
              key: String,
              fingerprint: String,
              expiresAtMillis: Long,
          ): IdempotencyClaimResult = IdempotencyClaimResult.Claimed(claimToken = "claim-1")

          override fun complete(
              key: String,
              claimToken: String,
              fingerprint: String,
              response: IdempotencyStoredResponse,
              expiresAtMillis: Long,
          ) {
            throw IllegalStateException("complete failed")
          }

          override fun remove(
              key: String,
              claimToken: String,
          ) {
            removeCalls.incrementAndGet()
          }
        }
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
            failOpen = true,
        )
    val chain = FilterChain { _, response ->
      (response as HttpServletResponse).status = 201
      response.writer.write("""{"created":true}""")
    }
    val request =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "fail-open-complete")
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, chain)

    assertEquals(201, response.status)
    assertEquals("""{"created":true}""", response.contentAsString)
    assertEquals(1, removeCalls.get())
  }

  @Test
  fun `filter should ignore remove failure on exception path when failOpen is true`() {
    val store =
        object : IdempotencyStore {
          override fun claim(
              key: String,
              fingerprint: String,
              expiresAtMillis: Long,
          ): IdempotencyClaimResult = IdempotencyClaimResult.Claimed(claimToken = "claim-1")

          override fun complete(
              key: String,
              claimToken: String,
              fingerprint: String,
              response: IdempotencyStoredResponse,
              expiresAtMillis: Long,
          ) {}

          override fun remove(
              key: String,
              claimToken: String,
          ) {
            throw IllegalStateException("remove failed")
          }
        }
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
            failOpen = true,
        )
    val chain = FilterChain { _, _ -> throw IllegalStateException("controller failure") }
    val request =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "fail-open-remove")
        }

    val error =
        assertFailsWith<IllegalStateException> {
          filter.doFilter(request, MockHttpServletResponse(), chain)
        }

    assertEquals("controller failure", error.message)
  }

  @Test
  fun `filter should add replay body omitted header when cached body is skipped`() {
    val store = InMemoryIdempotencyStore()
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
            maxCachedBodyBytes = 4,
        )
    val chain = FilterChain { _, response ->
      val http = response as HttpServletResponse
      http.status = 201
      http.contentType = "application/json"
      http.writer.write("""{"created":true}""")
    }

    val firstRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    firstRequest.addHeader("Idempotency-Key", "oversized-body")
    filter.doFilter(firstRequest, MockHttpServletResponse(), chain)

    val replayRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    replayRequest.addHeader("Idempotency-Key", "oversized-body")
    val replayResponse = MockHttpServletResponse()
    filter.doFilter(replayRequest, replayResponse, chain)

    assertEquals(201, replayResponse.status)
    assertEquals("true", replayResponse.getHeader("X-Idempotent-Replay"))
    assertEquals("true", replayResponse.getHeader("X-Idempotent-Replay-Body-Omitted"))
    assertEquals("", replayResponse.contentAsString)
  }

  @Test
  fun `filter should not replay set-cookie header`() {
    val store = InMemoryIdempotencyStore()
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
        )
    val chain = FilterChain { _, response ->
      val http = response as HttpServletResponse
      http.status = 201
      http.contentType = "application/json"
      http.addHeader("Set-Cookie", "SESSION=abc; Path=/; HttpOnly")
      http.writer.write("""{"created":true}""")
    }

    val firstRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    firstRequest.addHeader("Idempotency-Key", "cookie-replay")
    filter.doFilter(firstRequest, MockHttpServletResponse(), chain)

    val replayRequest = MockHttpServletRequest("POST", "/api/v1/orders")
    replayRequest.addHeader("Idempotency-Key", "cookie-replay")
    val replayResponse = MockHttpServletResponse()
    filter.doFilter(replayRequest, replayResponse, chain)

    assertEquals(201, replayResponse.status)
    assertEquals("true", replayResponse.getHeader("X-Idempotent-Replay"))
    assertTrue(replayResponse.getHeaders("Set-Cookie").isEmpty())
  }

  @Test
  fun `filter should use processing ttl for claim and replay ttl for complete`() {
    data class ExpiryCapture(
        val claimExpiresAtMillis: Long? = null,
        val completeExpiresAtMillis: Long? = null,
    )

    var capture = ExpiryCapture()
    val store =
        object : IdempotencyStore {
          override fun claim(
              key: String,
              fingerprint: String,
              expiresAtMillis: Long,
          ): IdempotencyClaimResult {
            capture = capture.copy(claimExpiresAtMillis = expiresAtMillis)
            return IdempotencyClaimResult.Claimed(claimToken = "claim-1")
          }

          override fun complete(
              key: String,
              claimToken: String,
              fingerprint: String,
              response: IdempotencyStoredResponse,
              expiresAtMillis: Long,
          ) {
            capture = capture.copy(completeExpiresAtMillis = expiresAtMillis)
          }

          override fun remove(
              key: String,
              claimToken: String,
          ) {}
        }
    val tickingClock =
        object : Clock() {
          private val values = longArrayOf(1_000L, 11_000L)
          private var index = 0

          override fun getZone(): ZoneId = ZoneOffset.UTC

          override fun withZone(zone: ZoneId): Clock = this

          override fun instant(): Instant {
            val i = index.coerceAtMost(values.lastIndex)
            index += 1
            return Instant.ofEpochMilli(values[i])
          }
        }
    val filter =
        IdempotencyFilter(
            store = store,
            fingerprintResolver = DefaultIdempotencyFingerprintResolver(),
            includeMethods = setOf("POST"),
            ttlSeconds = 60,
            processingTtlSeconds = 600,
            timeProvider = TimeProvider(defaultClock = tickingClock),
        )
    val chain = FilterChain { _, response -> (response as HttpServletResponse).status = 201 }
    val request =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "ttl-split")
        }

    filter.doFilter(request, MockHttpServletResponse(), chain)

    assertEquals(1_000L + (600L * 1_000L), capture.claimExpiresAtMillis)
    assertEquals(11_000L + (60L * 1_000L), capture.completeExpiresAtMillis)
  }
}
