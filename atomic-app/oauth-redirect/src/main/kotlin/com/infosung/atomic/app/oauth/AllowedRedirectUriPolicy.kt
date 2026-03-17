package com.infosung.atomic.app.oauth

import com.infosung.atomic.contract.exception.HttpStatusException
import java.net.URI
import java.util.Locale
import org.slf4j.LoggerFactory

internal object AllowedRedirectUriPolicy {
  private val log = LoggerFactory.getLogger(AllowedRedirectUriPolicy::class.java)

  fun validateConfiguredPrefixes(configuredPrefixes: List<String>) {
    val normalizedPrefixes = normalizeConfiguredPrefixes(configuredPrefixes)
    require(normalizedPrefixes.isNotEmpty()) {
      "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes must not be empty when redirect API is enabled."
    }
    normalizedPrefixes.forEach { parseAllowedRedirectPattern(it) }
    log.debug("Validated oauth redirect allowlist: prefixCount={}", normalizedPrefixes.size)
  }

  fun validateRedirectUri(
      redirectUri: String,
      configuredPrefixes: List<String>,
  ): String {
    val normalizedRedirectUri = redirectUri.trim()
    if (normalizedRedirectUri.isBlank()) {
      log.warn("Rejected oauth redirect because redirectUri is blank.")
      throw HttpStatusException(status = 400, message = "redirectUri is required.")
    }

    val candidateUri =
        parseCandidateUriOrThrow(
            value = normalizedRedirectUri,
            message = "redirectUri is invalid.",
        )
    val normalizedPrefixes = normalizeConfiguredPrefixes(configuredPrefixes)
    if (normalizedPrefixes.isEmpty()) {
      log.error("Redirect validation failed because allowlist is empty.")
      throw IllegalStateException(
          "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes must be configured.",
      )
    }

    val allowedPatterns =
        try {
          normalizedPrefixes.map { parseAllowedRedirectPattern(it) }
        } catch (e: IllegalArgumentException) {
          log.error(
              "Redirect validation failed because allowlist configuration is invalid: {}",
              e.message,
          )
          throw HttpStatusException(
              status = 400, message = e.message ?: "Invalid allowlist.", cause = e)
        }

    val matchedPattern = allowedPatterns.firstOrNull { it.matches(candidateUri) }
    if (matchedPattern == null) {
      log.warn(
          "Rejected oauth redirect URI: redirectUri={}, configuredPrefixCount={}",
          normalizedRedirectUri,
          allowedPatterns.size,
      )
      throw HttpStatusException(status = 400, message = "redirectUri is not allowed.")
    }

    log.debug(
        "Accepted oauth redirect URI: redirectUri={}, matchedPattern={}, scheme={}, host={}, port={}, path={}",
        normalizedRedirectUri,
        matchedPattern.raw,
        candidateUri.scheme,
        candidateUri.host,
        effectivePort(candidateUri),
        normalizePath(candidateUri.path),
    )
    return normalizedRedirectUri
  }

  private fun normalizeConfiguredPrefixes(configuredPrefixes: List<String>): List<String> =
      configuredPrefixes.map { it.trim() }.filter { it.isNotBlank() }

  private fun parseCandidateUriOrThrow(
      value: String,
      message: String,
  ): URI {
    val uri =
        runCatching { URI(value) }.getOrNull()
            ?: throw HttpStatusException(status = 400, message = message)
    if (!uri.isAbsolute || uri.scheme.isNullOrBlank()) {
      throw HttpStatusException(status = 400, message = message)
    }
    if (!uri.userInfo.isNullOrBlank()) {
      throw HttpStatusException(status = 400, message = message)
    }
    return uri
  }

  private fun parseAllowedRedirectPattern(raw: String): AllowedRedirectPattern {
    val uri =
        runCatching { URI(raw) }
            .getOrElse {
              throw IllegalArgumentException("Invalid allowed redirect URI entry: $raw", it)
            }
    if (!uri.isAbsolute || uri.scheme.isNullOrBlank()) {
      throw IllegalArgumentException("Allowed redirect URI must be absolute.")
    }
    if (!uri.userInfo.isNullOrBlank()) {
      throw IllegalArgumentException("Allowed redirect URI must not contain user info.")
    }
    if (!uri.rawQuery.isNullOrBlank() || !uri.rawFragment.isNullOrBlank()) {
      throw IllegalArgumentException("Allowed redirect URI must not include query or fragment.")
    }

    return AllowedRedirectPattern(
        raw = raw,
        scheme = uri.scheme.lowercase(Locale.ROOT),
        host = uri.host?.lowercase(Locale.ROOT),
        port = effectivePort(uri),
        pathPrefix = normalizeAllowedPathPrefix(uri.path),
    )
  }

  private fun effectivePort(uri: URI): Int {
    if (uri.port >= 0) {
      return uri.port
    }
    return when (uri.scheme.lowercase(Locale.ROOT)) {
      "http" -> 80
      "https" -> 443
      else -> -1
    }
  }

  private fun normalizeAllowedPathPrefix(path: String?): String {
    val normalized = normalizePath(path)
    return if (normalized.length > 1 && normalized.endsWith("/")) {
      normalized.dropLast(1)
    } else {
      normalized
    }
  }

  private fun normalizePath(path: String?): String {
    val raw = path?.trim().orEmpty()
    if (raw.isEmpty()) {
      return "/"
    }
    return if (raw.startsWith("/")) raw else "/$raw"
  }

  private data class AllowedRedirectPattern(
      val raw: String,
      val scheme: String,
      val host: String?,
      val port: Int,
      val pathPrefix: String,
  ) {
    fun matches(candidateUri: URI): Boolean {
      if (candidateUri.scheme.lowercase(Locale.ROOT) != scheme) {
        return false
      }
      val candidateHost = candidateUri.host?.lowercase(Locale.ROOT)
      if (candidateHost != host) {
        return false
      }
      if (effectivePort(candidateUri) != port) {
        return false
      }
      val candidatePath = normalizeCandidatePath(candidateUri.path)
      if (pathPrefix == "/") {
        return true
      }
      return candidatePath == pathPrefix || candidatePath.startsWith("$pathPrefix/")
    }

    private fun normalizeCandidatePath(path: String?): String {
      val raw = path?.trim().orEmpty()
      if (raw.isEmpty()) {
        return "/"
      }
      return if (raw.startsWith("/")) raw else "/$raw"
    }
  }
}
