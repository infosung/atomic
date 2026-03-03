package com.infosung.atomic.spring.web.ratelimit

import com.infosung.atomic.contract.time.TimeProvider
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.Locale
import org.slf4j.LoggerFactory

/** Servlet filter for request rate limiting. */
class RateLimitFilter(
    private val store: RateLimitStore,
    private val policyResolver: RateLimitPolicyResolver,
    private val keyResolver: RateLimitKeyResolver,
    private val timeProvider: TimeProvider = TimeProvider(),
    private val failOpen: Boolean = true,
    private val missingKeyPolicy: RateLimitMissingKeyPolicy = RateLimitMissingKeyPolicy.REJECT,
    private val responseBody: String = "Too many requests.",
    includeMethods: Set<String> = setOf("GET", "POST", "PUT", "PATCH", "DELETE"),
) : Filter {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val normalizedIncludeMethods = includeMethods.map { it.uppercase(Locale.ROOT) }.toSet()

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
    if (method == null || !normalizedIncludeMethods.contains(method)) {
      chain.doFilter(request, response)
      return
    }

    val policy = policyResolver.resolve(httpRequest)
    if (policy == null) {
      chain.doFilter(request, response)
      return
    }

    val actor = keyResolver.resolve(httpRequest)?.takeIf { it.isNotBlank() }
    if (actor == null) {
      when (missingKeyPolicy) {
        RateLimitMissingKeyPolicy.SKIP -> chain.doFilter(request, response)
        RateLimitMissingKeyPolicy.REJECT -> {
          log.debug(
              "RateLimit key is missing. Rejecting request due to missingKeyPolicy=REJECT: method={}, uri={}",
              httpRequest.method,
              httpRequest.requestURI,
          )
          writeBadRequest(httpResponse, "Rate-limit key is missing.")
        }
      }
      return
    }

    val pathKey =
        (policyResolver as? RateLimitPathKeyResolver)?.resolvePathKey(httpRequest)?.takeIf {
          it.isNotBlank()
        } ?: httpRequest.requestURI
    val key = "$actor|$method|$pathKey"
    val decision =
        try {
          store.consume(
              key = key,
              policy = policy,
              nowMillis = timeProvider.nowMillis(),
          )
        } catch (e: Exception) {
          if (failOpen) {
            log.warn(
                "RateLimit store failure. Passing request due to failOpen=true: method={}, uri={}",
                httpRequest.method,
                httpRequest.requestURI,
                e,
            )
            chain.doFilter(request, response)
            return
          }
          throw e
        }

    applyHeaders(httpResponse, decision)
    if (decision.allowed) {
      chain.doFilter(request, response)
      return
    }

    httpResponse.status = 429
    httpResponse.contentType = "text/plain;charset=UTF-8"
    if (!httpResponse.isCommitted) {
      httpResponse.writer.write(responseBody)
    }
  }

  private fun writeBadRequest(
      response: HttpServletResponse,
      message: String,
  ) {
    response.status = HttpServletResponse.SC_BAD_REQUEST
    response.contentType = "text/plain;charset=UTF-8"
    if (!response.isCommitted) {
      response.writer.write(message)
    }
  }

  private fun applyHeaders(
      response: HttpServletResponse,
      decision: RateLimitDecision,
  ) {
    response.setHeader("X-RateLimit-Limit", decision.limit.toString())
    response.setHeader("X-RateLimit-Remaining", decision.remaining.toString())
    response.setHeader("X-RateLimit-Reset", decision.resetAfterSeconds.toString())
    decision.retryAfterSeconds?.let { response.setHeader("Retry-After", it.toString()) }
  }
}
