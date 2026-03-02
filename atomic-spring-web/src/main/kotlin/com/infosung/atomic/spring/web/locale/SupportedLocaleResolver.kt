package com.infosung.atomic.spring.web.locale

import com.infosung.atomic.contract.header.ApiHeaderNames
import jakarta.servlet.http.HttpServletRequest
import java.util.Locale
import org.slf4j.LoggerFactory

/**
 * Locale resolution output.
 *
 * @property locale Resolved locale object.
 * @property code Language tag code.
 * @property displayName Human-readable locale name.
 */
data class SupportedLocaleResolution(
    val locale: Locale,
    val code: String,
    val displayName: String,
)

/** Resolves locale from custom language header/request locale using supported locale list. */
object SupportedLocaleResolver {
  private val log = LoggerFactory.getLogger(SupportedLocaleResolver::class.java)

  /**
   * Resolves locale in this order:
   * 1) `X-Custom-Language`
   * 2) request locale
   * 3) [defaultLocale]
   */
  fun resolveSupportedLocale(
      request: HttpServletRequest,
      supportedLocales: Collection<Locale>,
      defaultLocale: Locale = Locale.ENGLISH,
  ): SupportedLocaleResolution {
    if (supportedLocales.isEmpty()) {
      log.debug("supportedLocales is empty. Returning default locale={}", defaultLocale)
      return toResolution(defaultLocale)
    }

    val customLanguage = request.getHeader(ApiHeaderNames.HEADER_X_CUSTOM_LANGUAGE)
    if (!customLanguage.isNullOrBlank()) {
      val customLocale = Locale.forLanguageTag(customLanguage.trim().replace('_', '-'))
      val customMatched = matchSupportedLocale(customLocale, supportedLocales)
      if (customMatched != null) {
        log.trace(
            "Resolved locale from custom header: value={}, resolved={}",
            customLanguage,
            customMatched.toLanguageTag(),
        )
        return toResolution(customMatched)
      }
      log.debug(
          "Custom language header not in supported locales. Falling back to request locale: value={}",
          customLanguage,
      )
    }

    val requestMatched = matchSupportedLocale(request.locale, supportedLocales)
    if (requestMatched != null) {
      log.trace(
          "Resolved locale from request locale: locale={}, resolved={}",
          request.locale.toLanguageTag(),
          requestMatched.toLanguageTag(),
      )
      return toResolution(requestMatched)
    }

    log.debug(
        "No matching locale in supported locales. Returning default locale={}",
        defaultLocale.toLanguageTag(),
    )
    return toResolution(defaultLocale)
  }

  private fun matchSupportedLocale(
      candidate: Locale?,
      supportedLocales: Collection<Locale>
  ): Locale? {
    if (candidate == null || candidate.language.isBlank()) {
      return null
    }

    val candidateTag = candidate.toLanguageTag().lowercase(Locale.ROOT)
    supportedLocales
        .firstOrNull { it.toLanguageTag().lowercase(Locale.ROOT) == candidateTag }
        ?.let {
          return it
        }

    return supportedLocales.firstOrNull {
      it.language.equals(candidate.language, ignoreCase = true)
    }
  }

  private fun toResolution(locale: Locale): SupportedLocaleResolution {
    val code = locale.toLanguageTag()
    val displayName =
        locale.getDisplayLanguage(locale).takeIf { it.isNotBlank() }
            ?: locale.displayLanguage.takeIf { it.isNotBlank() }
            ?: code
    return SupportedLocaleResolution(locale = locale, code = code, displayName = displayName)
  }
}
