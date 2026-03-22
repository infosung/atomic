package com.infosung.atomic.spring.idempotency

import jakarta.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.ObjectMapper

class IdempotencyErrorContractTest {
  private val objectMapper = ObjectMapper()

  @Test
  fun `filter should return stable code when idempotency key is missing`() {
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

    val body = objectMapper.readTree(response.contentAsString)
    assertEquals(400, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("IDEMPOTENCY_KEY_REQUIRED", body["code"]?.stringValue())
    assertEquals("Idempotency-Key header is required.", body["message"]?.stringValue())
  }

  @Test
  fun `filter should return stable code when request is already processing`() {
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
        )
    val response = MockHttpServletResponse()
    val request =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "processing-key")
        }

    filter.doFilter(request, response, FilterChain { _, _ -> error("chain should not execute") })

    val body = objectMapper.readTree(response.contentAsString)
    assertEquals(409, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("IDEMPOTENCY_REQUEST_PROCESSING", body["code"]?.stringValue())
    assertEquals("Idempotent request is already processing.", body["message"]?.stringValue())
  }

  @Test
  fun `filter should return stable code when fingerprint mismatches`() {
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
    val chain = FilterChain { _, response -> response.resetBuffer() }

    val first =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "abc-2")
          addHeader("X-Fingerprint", "one")
        }
    filter.doFilter(first, MockHttpServletResponse(), chain)

    val second =
        MockHttpServletRequest("POST", "/api/v1/orders").apply {
          addHeader("Idempotency-Key", "abc-2")
          addHeader("X-Fingerprint", "two")
        }
    val secondResponse = MockHttpServletResponse()

    filter.doFilter(second, secondResponse, chain)

    val body = objectMapper.readTree(secondResponse.contentAsString)
    assertEquals(409, secondResponse.status)
    assertEquals("application/json", secondResponse.contentType)
    assertEquals("IDEMPOTENCY_FINGERPRINT_MISMATCH", body["code"]?.stringValue())
    assertEquals(
        "Idempotency key has been used with a different request.",
        body["message"]?.stringValue(),
    )
  }
}
