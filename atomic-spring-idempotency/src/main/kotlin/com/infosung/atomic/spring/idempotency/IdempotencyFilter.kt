package com.infosung.atomic.spring.idempotency

import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.contract.time.TimeProvider
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.Locale
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

/** Servlet filter that enforces one-time processing by idempotency key. */
class IdempotencyFilter(
    private val store: IdempotencyStore,
    private val fingerprintResolver: IdempotencyFingerprintResolver,
    private val timeProvider: TimeProvider = TimeProvider(),
    private val headerName: String = "Idempotency-Key",
    private val ttlSeconds: Long = 300,
    private val processingTtlSeconds: Long = 3_600,
    private val requireHeader: Boolean = true,
    private val includeMethods: Set<String> = setOf("POST"),
    private val failOpen: Boolean = true,
    private val replayHeaderName: String = "X-Idempotent-Replay",
    private val replayBodyOmittedHeaderName: String = "X-Idempotent-Replay-Body-Omitted",
    private val maxCachedBodyBytes: Int = 262_144,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : Filter {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val normalizedMethods = includeMethods.map { it.uppercase(Locale.ROOT) }.toSet()

  override fun doFilter(
      request: ServletRequest,
      response: ServletResponse,
      chain: FilterChain,
  ) {
    val httpRequest = request as? HttpServletRequest
    val httpResponse = response as? HttpServletResponse
    if (httpRequest == null || httpResponse == null) {
      chain.doFilter(request, response)
      return
    }

    val method = httpRequest.method?.uppercase(Locale.ROOT)
    if (method == null || !normalizedMethods.contains(method)) {
      chain.doFilter(request, response)
      return
    }

    val keyValue = httpRequest.getHeader(headerName)?.trim()
    if (keyValue.isNullOrBlank()) {
      if (requireHeader) {
        log.debug(
            "Idempotency key is missing. Rejecting request: method={}, uri={}",
            httpRequest.method,
            httpRequest.requestURI,
        )
        writeError(
            response = httpResponse,
            status = HttpServletResponse.SC_BAD_REQUEST,
            code = IdempotencyErrorCode.IDEMPOTENCY_KEY_REQUIRED.name,
            message = "$headerName header is required.",
        )
        return
      }
      chain.doFilter(request, response)
      return
    }

    val storageKey = "$method|${httpRequest.requestURI}|$keyValue"
    val fingerprint = fingerprintResolver.resolve(httpRequest)
    val claimExpiresAtMillis = timeProvider.nowMillis() + (processingTtlSeconds * 1_000)
    val claim =
        try {
          store.claim(
              key = storageKey,
              fingerprint = fingerprint,
              expiresAtMillis = claimExpiresAtMillis,
          )
        } catch (e: Exception) {
          if (failOpen) {
            log.warn(
                "Idempotency store claim failed. Passing request due to failOpen=true: method={}, uri={}",
                httpRequest.method,
                httpRequest.requestURI,
                e,
            )
            chain.doFilter(request, response)
            return
          }
          throw e
        }

    when (claim) {
      is IdempotencyClaimResult.Claimed ->
          executeAndStore(
              request = httpRequest,
              response = httpResponse,
              chain = chain,
              storageKey = storageKey,
              claimToken = claim.claimToken,
              fingerprint = fingerprint,
          )

      is IdempotencyClaimResult.Completed -> {
        writeStoredResponse(httpResponse, claim.response)
      }

      IdempotencyClaimResult.Processing -> {
        log.debug(
            "Idempotent request is already processing. Rejecting request: method={}, uri={}",
            httpRequest.method,
            httpRequest.requestURI,
        )
        writeError(
            response = httpResponse,
            status = HttpServletResponse.SC_CONFLICT,
            code = IdempotencyErrorCode.IDEMPOTENCY_REQUEST_PROCESSING.name,
            message = "Idempotent request is already processing.",
        )
      }

      IdempotencyClaimResult.FingerprintMismatch -> {
        log.debug(
            "Idempotency key fingerprint mismatch. Rejecting request: method={}, uri={}",
            httpRequest.method,
            httpRequest.requestURI,
        )
        writeError(
            response = httpResponse,
            status = HttpServletResponse.SC_CONFLICT,
            code = IdempotencyErrorCode.IDEMPOTENCY_FINGERPRINT_MISMATCH.name,
            message = "Idempotency key has been used with a different request.",
        )
      }
    }
  }

  private fun executeAndStore(
      request: HttpServletRequest,
      response: HttpServletResponse,
      chain: FilterChain,
      storageKey: String,
      claimToken: String,
      fingerprint: String,
  ) {
    val wrapper = BoundedBodyCaptureResponseWrapper(response, maxCachedBodyBytes)
    var removeTriggered = false
    try {
      chain.doFilter(request, wrapper)
      wrapper.flushBuffer()
      if (wrapper.status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
        removeTriggered = true
        removeClaim(
            key = storageKey,
            claimToken = claimToken,
            method = request.method,
            uri = request.requestURI,
            reason = "responseStatus=${wrapper.status}",
        )
      } else {
        val body = wrapper.capturedBody()
        val bodyOmitted = wrapper.isBodyOmittedForSizeLimit()
        val bodyToStore = if (bodyOmitted) ByteArray(0) else body
        if (bodyOmitted) {
          log.info(
              "Idempotency replay body omitted due to size limit: method={}, uri={}, capturedBytes={}, maxCachedBodyBytes={}",
              request.method,
              request.requestURI,
              wrapper.capturedBodyBytes(),
              maxCachedBodyBytes,
          )
        }
        val headers =
            wrapper.headerNames.associateWith { name -> wrapper.getHeaders(name).toList() }.toMap()
        completeClaim(
            key = storageKey,
            claimToken = claimToken,
            fingerprint = fingerprint,
            response =
                IdempotencyStoredResponse(
                    status = wrapper.status,
                    contentType = wrapper.contentType,
                    headers = headers,
                    body = bodyToStore,
                    bodyOmittedForSizeLimit = bodyOmitted,
                ),
            expiresAtMillis = timeProvider.nowMillis() + (ttlSeconds * 1_000),
            method = request.method,
            uri = request.requestURI,
        )
      }
    } catch (e: Exception) {
      if (!removeTriggered) {
        removeClaim(
            key = storageKey,
            claimToken = claimToken,
            method = request.method,
            uri = request.requestURI,
            reason = "exception",
            original = e,
        )
      }
      throw e
    }
  }

  private fun writeStoredResponse(
      response: HttpServletResponse,
      stored: IdempotencyStoredResponse,
  ) {
    response.status = stored.status
    if (!stored.contentType.isNullOrBlank()) {
      response.contentType = stored.contentType
    }
    stored.headers.forEach { (name, values) ->
      if (!isReplayableHeader(name)) {
        return@forEach
      }
      values.forEach { response.addHeader(name, it) }
    }
    response.setHeader(replayHeaderName, "true")
    if (stored.bodyOmittedForSizeLimit) {
      response.setHeader(replayBodyOmittedHeaderName, "true")
    }
    if (stored.body.isNotEmpty()) {
      response.outputStream.write(stored.body)
    }
  }

  private fun completeClaim(
      key: String,
      claimToken: String,
      fingerprint: String,
      response: IdempotencyStoredResponse,
      expiresAtMillis: Long,
      method: String?,
      uri: String?,
  ) {
    try {
      store.complete(
          key = key,
          claimToken = claimToken,
          fingerprint = fingerprint,
          response = response,
          expiresAtMillis = expiresAtMillis,
      )
    } catch (e: Exception) {
      if (failOpen) {
        log.warn(
            "Idempotency store complete failed. Passing response due to failOpen=true: method={}, uri={}",
            method,
            uri,
            e,
        )
        removeClaim(
            key = key,
            claimToken = claimToken,
            method = method,
            uri = uri,
            reason = "completeFailure",
        )
        return
      }
      throw e
    }
  }

  private fun removeClaim(
      key: String,
      claimToken: String,
      method: String?,
      uri: String?,
      reason: String,
      original: Exception? = null,
  ) {
    try {
      store.remove(key = key, claimToken = claimToken)
    } catch (e: Exception) {
      if (failOpen) {
        log.warn(
            "Idempotency store remove failed. Keeping failOpen=true: method={}, uri={}, reason={}",
            method,
            uri,
            reason,
            e,
        )
      } else if (original != null) {
        original.addSuppressed(e)
      } else {
        throw e
      }
    }
  }

  private fun writeError(
      response: HttpServletResponse,
      status: Int,
      code: String,
      message: String,
  ) {
    response.status = status
    response.contentType = "application/json"
    if (!response.isCommitted) {
      response.writer.write(
          objectMapper.writeValueAsString(
              BaseResponse<Any>(
                  code = code,
                  message = message,
              ),
          ),
      )
    }
  }

  private fun isReplayableHeader(name: String): Boolean {
    return !NON_REPLAYABLE_HEADERS.contains(name.lowercase(Locale.ROOT))
  }

  companion object {
    private val NON_REPLAYABLE_HEADERS =
        setOf(
            "content-length",
            "content-type",
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "set-cookie",
            "date",
            "server",
        )
  }
}
