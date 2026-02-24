package com.infosung.atomic.spring.security.channel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class HostBasedClientChannelResolverTest {
  private val resolver =
      HostBasedClientChannelResolver(
          webDomains =
              listOf(
                  "www.totp.co.kr",
                  "totp.co.kr",
                  "devweb.totp.co.kr",
                  "localhost:3000",
              ),
          apiDomains =
              listOf(
                  "api.totp.co.kr",
                  "devapi.totp.co.kr",
                  "localhost:8000",
                  "http://infosungui-macbookpro.local:8080",
              ),
      )

  @Test
  fun `should resolve web when origin matches configured web domain`() {
    val request =
        MockHttpServletRequest("GET", "/api/v1/test").apply {
          serverName = "api.totp.co.kr"
          addHeader("Host", "api.totp.co.kr")
          addHeader("Origin", "https://www.totp.co.kr")
        }

    assertEquals(ClientChannel.WEB, resolver.resolve(request))
  }

  @Test
  fun `should resolve app when no browser origin headers exist`() {
    val request =
        MockHttpServletRequest("GET", "/api/v1/test").apply {
          serverName = "api.totp.co.kr"
          addHeader("Host", "api.totp.co.kr")
        }

    assertEquals(ClientChannel.APP, resolver.resolve(request))
  }

  @Test
  fun `should resolve unknown when api host is outside configured list`() {
    val request =
        MockHttpServletRequest("GET", "/api/v1/test").apply {
          serverName = "unexpected.totp.co.kr"
          addHeader("Host", "unexpected.totp.co.kr")
          addHeader("Origin", "https://www.totp.co.kr")
        }

    assertEquals(ClientChannel.UNKNOWN, resolver.resolve(request))
  }
}
