package com.infosung.atomic.spring.security.filter

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.channel.ClientChannel
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.jwt.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import tools.jackson.databind.ObjectMapper

class SecurityFilterTest {
  private val timeProvider = TimeProvider()

  @BeforeEach
  fun setupClock() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
  }

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
    timeProvider.reset()
  }

  @Test
  fun `exclude url should pass chain without touching authentication or cookies`() {
    val jwtProvider = jwtProvider()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = listOf("GET /api/exclude"),
            timeProvider = timeProvider,
        )

    val refreshToken = jwtProvider.createJwtDto(id = "123", subject = "USER").refreshToken
    val request =
        MockHttpServletRequest("GET", "/api/exclude").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.token")
          setCookies(Cookie("refreshToken", refreshToken))
        }
    val response = MockHttpServletResponse()
    var chainCalled = false
    val chain = FilterChain { _, _ -> chainCalled = true }

    filter.doFilter(request, response, chain)

    assertTrue(chainCalled)
    assertNull(SecurityContextHolder.getContext().authentication)
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `unknown channel should refresh using refresh token when access token is missing`() {
    val jwtProvider = jwtProvider()
    val objectMapper = ObjectMapper()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = objectMapper,
            excludeUrls = emptyList(),
            timeProvider = timeProvider,
        )

    val refreshToken = jwtProvider.createJwtDto(id = "123", subject = "USER").refreshToken
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          setCookies(Cookie("refreshToken", refreshToken))
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    val setCookieHeaders = response.getHeaders("Set-Cookie")
    assertTrue(setCookieHeaders.any { it.startsWith("accessToken=") })
    assertTrue(setCookieHeaders.any { it.startsWith("refreshToken=") })
    assertEquals("123", authenticatedUserId())
  }

  @Test
  fun `authorization header should authenticate without issuing cookies`() {
    val jwtProvider = jwtProvider()
    val objectMapper = ObjectMapper()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = objectMapper,
            excludeUrls = emptyList(),
            timeProvider = timeProvider,
        )
    val accessToken = jwtProvider.createJwtDto(id = "123", subject = "USER").accessToken

    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "bearer $accessToken")
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertNotNull(SecurityContextHolder.getContext().authentication)
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `unknown channel should prioritize authorization over cookies`() {
    val jwtProvider = jwtProvider()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.UNKNOWN },
            timeProvider = timeProvider,
        )
    val headerToken = jwtProvider.createJwtDto(id = "1", subject = "USER").accessToken
    val accessCookieToken = jwtProvider.createJwtDto(id = "2", subject = "USER").accessToken
    val refreshCookieToken = jwtProvider.createJwtDto(id = "3", subject = "USER").refreshToken
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer $headerToken")
          setCookies(
              Cookie("accessToken", accessCookieToken),
              Cookie("refreshToken", refreshCookieToken),
          )
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertEquals("1", authenticatedUserId())
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `unknown channel should use access token cookie when authorization is missing`() {
    val jwtProvider = jwtProvider()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.UNKNOWN },
            timeProvider = timeProvider,
        )
    val accessCookieToken = jwtProvider.createJwtDto(id = "2", subject = "USER").accessToken
    val refreshCookieToken = jwtProvider.createJwtDto(id = "3", subject = "USER").refreshToken
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          setCookies(
              Cookie("accessToken", accessCookieToken),
              Cookie("refreshToken", refreshCookieToken),
          )
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertEquals("2", authenticatedUserId())
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `web channel should ignore authorization header`() {
    val jwtProvider = jwtProvider()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.WEB },
            timeProvider = timeProvider,
        )
    val accessToken = jwtProvider.createJwtDto(id = "123", subject = "USER").accessToken
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertNull(SecurityContextHolder.getContext().authentication)
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `web channel should prioritize access token cookie over authorization header`() {
    val jwtProvider = jwtProvider()
    val cookieAccessToken = jwtProvider.createJwtDto(id = "10", subject = "USER").accessToken
    val headerAccessToken = jwtProvider.createJwtDto(id = "20", subject = "USER").accessToken
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.WEB },
            timeProvider = timeProvider,
        )
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer $headerAccessToken")
          setCookies(Cookie("accessToken", cookieAccessToken))
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertEquals("10", authenticatedUserId())
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  @Test
  fun `invalid access token should return unauthorized json response`() {
    val objectMapper = ObjectMapper()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider(),
            objectMapper = objectMapper,
            excludeUrls = emptyList(),
            timeProvider = timeProvider,
        )
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.token")
        }
    val response = MockHttpServletResponse()
    var chainCalled = false
    val chain = FilterChain { _, _ -> chainCalled = true }

    filter.doFilter(request, response, chain)

    val body = objectMapper.readTree(response.contentAsString)
    assertTrue(!chainCalled)
    assertEquals(401, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("UNAUTHORIZED", body["code"].asText())
    assertEquals("UNAUTHORIZED", body["message"].asText())
    assertNull(SecurityContextHolder.getContext().authentication)
  }

  @Test
  fun `refresh token used as bearer token should return unauthorized json response`() {
    val jwtProvider = jwtProvider()
    val objectMapper = ObjectMapper()
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = objectMapper,
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.APP },
            timeProvider = timeProvider,
        )
    val refreshToken = jwtProvider.createJwtDto(id = "123", subject = "USER").refreshToken
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          addHeader(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
        }
    val response = MockHttpServletResponse()
    var chainCalled = false
    val chain = FilterChain { _, _ -> chainCalled = true }

    filter.doFilter(request, response, chain)

    val body = objectMapper.readTree(response.contentAsString)
    assertTrue(!chainCalled)
    assertEquals(401, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("UNAUTHORIZED", body["code"].asText())
    assertEquals("UNAUTHORIZED", body["message"].asText())
    assertNull(SecurityContextHolder.getContext().authentication)
  }

  @Test
  fun `invalid refresh token cookie should propagate exception`() {
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider(),
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.UNKNOWN },
            timeProvider = timeProvider,
        )
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          setCookies(Cookie("refreshToken", "invalid.refresh.token"))
        }
    val response = MockHttpServletResponse()
    var chainCalled = false
    val chain = FilterChain { _, _ -> chainCalled = true }

    assertThrows(Exception::class.java) { filter.doFilter(request, response, chain) }
    assertTrue(!chainCalled)
    assertNull(SecurityContextHolder.getContext().authentication)
  }

  @Test
  fun `app channel should not authenticate when only cookies are provided`() {
    val jwtProvider = jwtProvider()
    val accessToken = jwtProvider.createJwtDto(id = "123", subject = "USER").accessToken
    val refreshToken = jwtProvider.createJwtDto(id = "123", subject = "USER").refreshToken
    val filter =
        SecurityFilter(
            jwtProvider = jwtProvider,
            objectMapper = ObjectMapper(),
            excludeUrls = emptyList(),
            clientChannelResolver = ClientChannelResolver { ClientChannel.APP },
            timeProvider = timeProvider,
        )
    val request =
        MockHttpServletRequest("GET", "/api/test").apply {
          setCookies(
              Cookie("accessToken", accessToken),
              Cookie("refreshToken", refreshToken),
          )
        }
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, MockFilterChain())

    assertNull(SecurityContextHolder.getContext().authentication)
    assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
  }

  private fun jwtProvider(): JwtProvider =
      JwtProvider(
          accessKey = "a".repeat(80),
          refreshKey = "b".repeat(80),
          accessExpiredSecond = 60,
          refreshExpiredSecond = 600,
          timeProvider = timeProvider,
      )

  private fun authenticatedUserId(): String? {
    val principal = SecurityContextHolder.getContext().authentication?.principal as? User
    return principal?.username
  }
}
