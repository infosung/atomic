package com.infosung.atomic.spring.security.util

import com.infosung.atomic.contract.exception.HttpUnauthorizedException
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.jwt.JwtDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User

class SecurityUtilTest {
  private val fixedNow: Instant = Instant.parse("2026-02-24T00:00:00Z")
  private val timeProvider = TimeProvider()

  private fun useFixedClock(now: Instant = fixedNow) {
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
  }

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
    timeProvider.reset()
  }

  @Test
  fun `tokenInHttpOnlyCookie should create secure cookie headers`() {
    useFixedClock()
    val now = timeProvider.nowMillis()
    val headers =
        SecurityUtil.tokenInHttpOnlyCookie(
            JwtDto(
                id = "1",
                accessToken = "a.b.c",
                refreshToken = "d.e.f",
                accessExpiredTime = now + 60_000,
                refreshExpiredTime = now + 120_000,
            ),
            timeProvider = timeProvider,
        )

    val setCookies = headers["Set-Cookie"].orEmpty()
    assertEquals(2, setCookies.size)
    assertTrue(
        setCookies.any {
          it.startsWith("accessToken=") && it.contains("HttpOnly") && it.contains("Secure")
        },
    )
    assertTrue(
        setCookies.any {
          it.startsWith("refreshToken=") && it.contains("HttpOnly") && it.contains("Secure")
        },
    )
  }

  @Test
  fun `tokenInHttpOnlyCookie should clamp max age to zero when token is already expired`() {
    useFixedClock()
    val now = timeProvider.nowMillis()
    val headers =
        SecurityUtil.tokenInHttpOnlyCookie(
            JwtDto(
                id = "1",
                accessToken = "a.b.c",
                refreshToken = "d.e.f",
                accessExpiredTime = now - 1_000,
                refreshExpiredTime = now - 1_000,
            ),
            timeProvider = timeProvider,
        )

    val setCookies = headers["Set-Cookie"].orEmpty()
    assertEquals(2, setCookies.size)
    assertTrue(setCookies.all { it.contains("Max-Age=0") })
    assertTrue(setCookies.none { it.contains("Max-Age=-") })
  }

  @Test
  fun `removeHttpOnlyCookie should expire both cookies`() {
    val headers = SecurityUtil.removeHttpOnlyCookie()
    val setCookies = headers["Set-Cookie"].orEmpty()

    assertEquals(2, setCookies.size)
    assertTrue(setCookies.any { it.startsWith("accessToken=") && it.contains("Max-Age=0") })
    assertTrue(setCookies.any { it.startsWith("refreshToken=") && it.contains("Max-Age=0") })
  }

  @Test
  fun `cookie policy should override defaults`() {
    useFixedClock()
    val now = timeProvider.nowMillis()
    val headers =
        SecurityUtil.tokenInHttpOnlyCookie(
            jwtDto =
                JwtDto(
                    id = "1",
                    accessToken = "a.b.c",
                    refreshToken = "d.e.f",
                    accessExpiredTime = now + 60_000,
                    refreshExpiredTime = now + 120_000,
                ),
            cookiePolicy =
                SecurityCookiePolicy(
                    sameSite = "Lax",
                    secure = false,
                    domain = "api.totp.co.kr",
                ),
            timeProvider = timeProvider,
        )

    val setCookies = headers["Set-Cookie"].orEmpty()
    assertEquals(2, setCookies.size)
    assertTrue(setCookies.all { it.contains("SameSite=Lax") })
    assertTrue(setCookies.all { it.contains("Domain=api.totp.co.kr") })
    assertTrue(setCookies.none { it.contains("Secure") })
  }

  @Test
  fun `userIdOrNull and userId should read value from security context`() {
    val principal = User("42", "", listOf(SimpleGrantedAuthority("ROLE_USER")))
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken(principal, "", principal.authorities)

    assertEquals(42L, SecurityUtil.userIdOrNull())
    assertEquals(42L, SecurityUtil.userId())
  }

  @Test
  fun `userId should throw unauthorized when principal is missing`() {
    assertFailsWith<HttpUnauthorizedException> { SecurityUtil.userId() }
  }

  @Test
  fun `tokenInHttpOnlyCookie should use configured clock for max age`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    useFixedClock(now)

    val headers =
        SecurityUtil.tokenInHttpOnlyCookie(
            JwtDto(
                id = "1",
                accessToken = "a.b.c",
                refreshToken = "d.e.f",
                accessExpiredTime = now.plusSeconds(40).toEpochMilli(),
                refreshExpiredTime = now.plusSeconds(70).toEpochMilli(),
            ),
            timeProvider = timeProvider,
        )

    val setCookies = headers["Set-Cookie"].orEmpty()
    assertTrue(setCookies.any { it.startsWith("accessToken=") && it.contains("Max-Age=30") })
    assertTrue(setCookies.any { it.startsWith("refreshToken=") && it.contains("Max-Age=60") })
  }
}
