package com.infosung.atomic.oauth.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OauthRequestSupportTest {
  @Test
  fun `encodeQuery should return base url when params are empty`() {
    val url = encodeQuery("https://example.com/path", emptyMap())
    assertEquals("https://example.com/path", url)
  }

  @Test
  fun `encodeQuery should encode keys and values`() {
    val url =
        encodeQuery(
            baseUrl = "https://example.com/path",
            params = mapOf("a b" to "c+d", "lang" to "ko-KR"),
        )

    assertTrue(url.startsWith("https://example.com/path?"))
    assertTrue(url.contains("a+b=c%2Bd"))
    assertTrue(url.contains("lang=ko-KR"))
  }

  @Test
  fun `parseScopes should split trim and de-duplicate`() {
    val scopes = parseScopes("openid email  profile email")
    assertEquals(setOf("openid", "email", "profile"), scopes)
  }

  @Test
  fun `parseScopes should return empty set for null or blank input`() {
    assertTrue(parseScopes(null).isEmpty())
    assertTrue(parseScopes("   ").isEmpty())
  }
}
