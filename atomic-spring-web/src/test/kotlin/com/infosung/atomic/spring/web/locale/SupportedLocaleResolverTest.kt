package com.infosung.atomic.spring.web.locale

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest

class SupportedLocaleResolverTest {
  @Test
  fun `resolveSupportedLocale should use server supported locales with custom header priority`() {
    val supported = listOf(Locale.ENGLISH, Locale.JAPANESE)
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addPreferredLocale(Locale.ENGLISH)
          addHeader("X-Custom-Language", "ja-JP")
        }

    val resolved = SupportedLocaleResolver.resolveSupportedLocale(request, supported)

    assertEquals(Locale.JAPANESE, resolved.locale)
    assertEquals("ja", resolved.code)
    assertEquals("日本語", resolved.displayName)
  }

  @Test
  fun `resolveSupportedLocale should fallback to request locale when custom header is unsupported`() {
    val supported = listOf(Locale.ENGLISH, Locale.JAPANESE)
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addPreferredLocale(Locale.JAPAN)
          addHeader("X-Custom-Language", "ko")
        }

    val resolved = SupportedLocaleResolver.resolveSupportedLocale(request, supported)

    assertEquals(Locale.JAPANESE, resolved.locale)
    assertEquals("ja", resolved.code)
    assertEquals("日本語", resolved.displayName)
  }

  @Test
  fun `resolveSupportedLocale should return provided default locale when no match`() {
    val supported = listOf(Locale.ENGLISH, Locale.JAPANESE)
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply { addPreferredLocale(Locale.GERMANY) }

    val resolved =
        SupportedLocaleResolver.resolveSupportedLocale(
            request = request,
            supportedLocales = supported,
            defaultLocale = Locale.JAPANESE,
        )

    assertEquals(Locale.JAPANESE, resolved.locale)
    assertEquals("ja", resolved.code)
    assertEquals("日本語", resolved.displayName)
  }
}
