package com.infosung.atomic.spring.web.header

import java.util.Collections
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.mock.web.MockHttpServletRequest

class RequestLanguageResolverTest {
  @Test
  fun `resolvePreferredLanguageTag should prefer X-Custom-Language header and canonicalize it`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-Custom-Language", "ja_JP")
          addPreferredLocale(Locale.KOREA)
        }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertEquals("ja-JP", resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should fallback to servlet preferred locale`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addPreferredLocale(Locale.KOREA)
        }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertEquals("ko-KR", resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should ignore blank custom header and fallback to servlet preferred locale`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-Custom-Language", "   ")
          addPreferredLocale(Locale.US)
        }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertEquals("en-US", resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should fallback to first servlet preferred locale`() {
    val request =
        object : MockHttpServletRequest("GET", "/v1/test") {
          override fun getLocales(): java.util.Enumeration<Locale> =
              Collections.enumeration(listOf(Locale.US, Locale.KOREA))
        }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertEquals("en-US", resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should fallback when custom header is malformed`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-Custom-Language", "???")
          addPreferredLocale(Locale.CANADA_FRENCH)
        }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertEquals("fr-CA", resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should return null when all candidates are unusable`() {
    val request =
        object : MockHttpServletRequest("GET", "/v1/test") {
          override fun getLocales(): java.util.Enumeration<Locale> = Collections.emptyEnumeration()
        }.apply { addHeader("X-Custom-Language", "   ") }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertNull(resolved)
  }

  @Test
  fun `resolvePreferredLanguageTag should return null when custom header and servlet locales are unusable`() {
    val request =
        object : MockHttpServletRequest("GET", "/v1/test") {
          override fun getLocales(): java.util.Enumeration<Locale> = Collections.emptyEnumeration()
        }.apply { addHeader("X-Custom-Language", "*") }

    val resolved = RequestLanguageResolver.resolvePreferredLanguageTag(request)

    assertNull(resolved)
  }
}
