package com.infosung.atomic.spring.web.ratelimit

import com.infosung.atomic.spring.web.header.getClientIp
import jakarta.servlet.http.HttpServletRequest
import java.util.Locale

/**
 * Resolves actor key used by rate-limit.
 *
 * Returning null follows [RateLimitMissingKeyPolicy] (default `REJECT`).
 */
fun interface RateLimitKeyResolver {
  fun resolve(request: HttpServletRequest): String?
}

/** Resolves policy for request. Returning null skips limit for the request. */
fun interface RateLimitPolicyResolver {
  fun resolve(request: HttpServletRequest): RateLimitPolicy?
}

/** Resolves request path key segment for storage-key composition. */
fun interface RateLimitPathKeyResolver {
  fun resolvePathKey(request: HttpServletRequest): String?
}

/**
 * Uses client IP as rate-limit key.
 *
 * By default this uses [HttpServletRequest.remoteAddr] only, because forwarded headers are
 * client-controlled unless the app is behind a trusted proxy that rewrites them.
 */
class IpRateLimitKeyResolver(
    private val trustForwardedHeaders: Boolean = false,
) : RateLimitKeyResolver {
  override fun resolve(request: HttpServletRequest): String? {
    if (trustForwardedHeaders) {
      return request.getClientIp()
    }
    return request.remoteAddr?.trim()?.takeIf { it.isNotBlank() }
  }
}

/** Uses a request header as rate-limit key. */
class HeaderRateLimitKeyResolver(
    private val headerName: String,
) : RateLimitKeyResolver {
  init {
    require(headerName.isNotBlank()) { "headerName must not be blank." }
  }

  override fun resolve(request: HttpServletRequest): String? {
    return request.getHeader(headerName)?.trim()?.takeIf { it.isNotBlank() }
  }
}

/** Resolves policy by path-prefix/method rules with one required default policy. */
class PathPrefixRateLimitPolicyResolver(
    private val defaultPolicy: RateLimitPolicy,
    private val rules: List<RateLimitRule> = emptyList(),
    private val excludedPathPrefixes: Set<String> = emptySet(),
    private val pathKeyStrategy: RateLimitPathKeyStrategy = RateLimitPathKeyStrategy.RULE_PREFIX,
) : RateLimitPolicyResolver, RateLimitPathKeyResolver {
  override fun resolve(request: HttpServletRequest): RateLimitPolicy? {
    val path = request.requestURI ?: return defaultPolicy
    if (isExcluded(path)) {
      return null
    }
    val method = request.method?.uppercase(Locale.ROOT)
    val matchedRule = findMatchedRule(path = path, method = method)
    return matchedRule?.policy ?: defaultPolicy
  }

  override fun resolvePathKey(request: HttpServletRequest): String? {
    val path = request.requestURI ?: return "default"
    if (isExcluded(path)) {
      return null
    }
    return when (pathKeyStrategy) {
      RateLimitPathKeyStrategy.REQUEST_URI -> path
      RateLimitPathKeyStrategy.RULE_PREFIX -> {
        val method = request.method?.uppercase(Locale.ROOT)
        findMatchedRule(path = path, method = method)?.pathPrefix ?: "default"
      }
    }
  }

  private fun isExcluded(path: String): Boolean =
      excludedPathPrefixes.any { prefix -> matchesPathPrefix(path, prefix) }

  private fun findMatchedRule(
      path: String,
      method: String?,
  ): RateLimitRule? {
    return rules.firstOrNull { rule ->
      val methodMatched =
          rule.methods.isEmpty() || (method != null && rule.methods.contains(method))
      val pathMatched =
          rule.pathPrefix.isNullOrBlank() ||
              matchesPathPrefix(path = path, prefix = rule.pathPrefix)
      methodMatched && pathMatched
    }
  }

  private fun matchesPathPrefix(
      path: String,
      prefix: String?,
  ): Boolean {
    val normalizedPrefix = prefix?.trim().orEmpty()
    if (normalizedPrefix.isBlank()) {
      return true
    }
    if (normalizedPrefix.endsWith("/")) {
      return path.startsWith(normalizedPrefix)
    }
    return path == normalizedPrefix || path.startsWith("$normalizedPrefix/")
  }
}
