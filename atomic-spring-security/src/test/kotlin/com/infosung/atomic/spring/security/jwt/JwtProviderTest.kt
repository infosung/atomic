package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.exception.HttpTokenNotExpiredException
import com.infosung.atomic.contract.time.TimeProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach

class JwtProviderTest {
  private val originalTimeZoneId: String = TimeZone.getDefault().id
  private val timeProvider = TimeProvider()

  @AfterEach
  fun resetTimeProvider() {
    timeProvider.reset()
    TimeZone.setDefault(TimeZone.getTimeZone(originalTimeZoneId))
  }

  private val provider =
      JwtProvider(
          accessKey = "a".repeat(80),
          refreshKey = "b".repeat(80),
          accessExpiredSecond = 60,
          refreshExpiredSecond = 600,
          timeProvider = timeProvider,
      )

  @Test
  fun `create and parse tokens should keep id subject and service name`() {
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")
    val accessClaims = provider.getAccessClaims(jwtDto.accessToken)
    val refreshClaims = provider.getRefreshClaims(jwtDto.refreshToken)

    assertEquals("123", accessClaims.id)
    assertEquals("USER", accessClaims.subject)
    assertEquals("InfosungAtomic", accessClaims["service_name"])

    assertEquals("123", refreshClaims.id)
    assertEquals("USER", refreshClaims.subject)
  }

  @Test
  fun `create and parse tokens should use injected service name`() {
    val customProvider =
        JwtProvider(
            accessKey = "a".repeat(80),
            refreshKey = "b".repeat(80),
            accessExpiredSecond = 60,
            refreshExpiredSecond = 600,
            serviceName = "totp",
            timeProvider = timeProvider,
        )

    val jwtDto = customProvider.createJwtDto(id = "123", subject = "USER")
    val accessClaims = customProvider.getAccessClaims(jwtDto.accessToken)

    assertEquals("totp", accessClaims["service_name"])
    assertEquals("totp", accessClaims.issuer)
  }

  @Test
  fun `getExpiredClaims should fail when token is not expired`() {
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    val exception =
        assertFailsWith<HttpTokenNotExpiredException> {
          provider.getExpiredClaims(jwtDto.accessToken)
        }
    assertTrue(
        exception.message.contains("not expired yet", ignoreCase = true) ||
            exception.cause?.message?.contains("not expired yet", ignoreCase = true) == true,
    )
  }

  @Test
  fun `getAccessClaims should fail for invalid token`() {
    assertFailsWith<HttpInvalidTokenException> { provider.getAccessClaims("invalid.token") }
  }

  @Test
  fun `createJwtDto should use configured clock`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))

    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    assertEquals(now.plusSeconds(60).toEpochMilli(), jwtDto.accessExpiredTime)
    assertEquals(now.plusSeconds(600).toEpochMilli(), jwtDto.refreshExpiredTime)
  }

  @Test
  fun `createJwtDto should keep same expiration epoch across timezones`() {
    val fixedNow = Instant.parse("2026-02-24T00:00:00Z")
    val expectedIssuedAt = Date.from(fixedNow)
    val expectedAccessExp = Date.from(fixedNow.plusSeconds(60))
    val expectedRefreshExp = Date.from(fixedNow.plusSeconds(600))
    timeProvider.configureClock(Clock.fixed(fixedNow, ZoneOffset.UTC))

    val zoneIds = listOf("UTC", "Asia/Seoul", "America/Los_Angeles")
    val results =
        zoneIds.map { zoneId ->
          TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
          provider.createJwtDto(id = "123", subject = "USER")
        }

    results.forEach { jwtDto ->
      assertEquals(fixedNow.plusSeconds(60).toEpochMilli(), jwtDto.accessExpiredTime)
      assertEquals(fixedNow.plusSeconds(600).toEpochMilli(), jwtDto.refreshExpiredTime)

      val accessClaims = provider.getAccessClaims(jwtDto.accessToken)
      assertEquals(expectedIssuedAt, accessClaims.issuedAt)
      assertEquals(expectedAccessExp, accessClaims.expiration)

      val refreshClaims = provider.getRefreshClaims(jwtDto.refreshToken)
      assertEquals(expectedIssuedAt, refreshClaims.issuedAt)
      assertEquals(expectedRefreshExp, refreshClaims.expiration)
    }

    assertEquals(results[0].accessToken, results[1].accessToken)
    assertEquals(results[1].accessToken, results[2].accessToken)
    assertEquals(results[0].refreshToken, results[1].refreshToken)
    assertEquals(results[1].refreshToken, results[2].refreshToken)
  }
}
