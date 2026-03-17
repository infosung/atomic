package com.infosung.atomic.spring.web.header

import com.infosung.atomic.contract.header.ApiHeaderNames
import jakarta.servlet.http.HttpServletRequest
import java.util.Locale
import org.slf4j.LoggerFactory

/**
 * Resolves a single request language hint string from inbound headers.
 *
 * Resolution order:
 * 1) `X-Custom-Language`
 * 2) first Servlet-preferred locale from `HttpServletRequest.getLocales()`
 */
object RequestLanguageResolver {
  private val log = LoggerFactory.getLogger(RequestLanguageResolver::class.java)

  fun resolvePreferredLanguageTag(request: HttpServletRequest): String? {
    val customLanguage =
        request.getHeader(ApiHeaderNames.HEADER_X_CUSTOM_LANGUAGE)?.trim()?.takeIf { it.isNotBlank() }
    if (customLanguage != null) {
      val resolved = canonicalizeLanguageTag(customLanguage)
      if (resolved != null) {
        log.trace(
            "Resolved request language hint from custom header: raw={}, resolved={}",
            customLanguage,
            resolved,
        )
        return resolved
      }
      log.debug(
          "Custom language header is not a usable language tag. Falling back to servlet preferred locales: raw={}",
          customLanguage,
      )
    }

    val locales = request.locales
    while (locales.hasMoreElements()) {
      val candidate = locales.nextElement()
      val resolved = canonicalizeLocale(candidate)
      if (resolved != null) {
        log.trace(
            "Resolved request language hint from servlet preferred locale: locale={}, resolved={}",
            candidate,
            resolved,
        )
        return resolved
      }
      log.debug(
          "Ignoring servlet preferred locale because it is not usable as a language tag: locale={}",
          candidate,
      )
    }

    log.trace("No usable request language hint found from custom header or servlet preferred locales. Returning null.")
    return null
  }

  private fun canonicalizeLanguageTag(rawValue: String): String? {
    val normalized = rawValue.trim().replace('_', '-')
    if (normalized.isBlank()) {
      return null
    }

    return canonicalizeLocale(Locale.forLanguageTag(normalized))
  }

  private fun canonicalizeLocale(locale: Locale): String? {
    if (locale.language.isBlank()) {
      return null
    }

    val tag = locale.toLanguageTag()
    if (tag.isBlank() || tag.equals("und", ignoreCase = true)) {
      return null
    }

    return tag
  }
}
