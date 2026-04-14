package com.infosung.atomic.spring.security.jwt

import com.infosung.atomic.contract.exception.HttpInvalidTokenException
import com.infosung.atomic.contract.exception.HttpTokenNotExpiredException
import com.infosung.atomic.contract.time.TimeProvider
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
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
    assertEquals("InfosungAtomic", accessClaims.claims["service_name"])

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

    assertEquals("totp", accessClaims.claims["service_name"])
    assertEquals("totp", accessClaims.claims["iss"])
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
  fun `getExpiredClaims should return claims when access token is expired`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    timeProvider.configureClock(Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC))
    val expiredClaims = provider.getExpiredClaims(jwtDto.accessToken)

    assertEquals("123", expiredClaims.id)
    assertEquals("USER", expiredClaims.subject)
    assertEquals(now.plusSeconds(60), expiredClaims.expiresAt)
  }

  @Test
  fun `getAccessClaims should fail for invalid token`() {
    assertFailsWith<HttpInvalidTokenException> { provider.getAccessClaims("invalid.token") }
  }

  @Test
  fun `getAccessClaims should fail when access token is expired`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    timeProvider.configureClock(Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC))
    val exception =
        assertFailsWith<HttpInvalidTokenException> { provider.getAccessClaims(jwtDto.accessToken) }

    assertTrue(
        exception.message.contains("expired", ignoreCase = true) ||
            exception.cause?.message?.contains("expired", ignoreCase = true) == true,
    )
  }

  @Test
  fun `getAccessClaims should fail at exact expiration instant`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    timeProvider.configureClock(Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC))
    assertFailsWith<HttpInvalidTokenException> { provider.getAccessClaims(jwtDto.accessToken) }
  }

  @Test
  fun `getRefreshClaims should fail when refresh token is expired`() {
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")

    timeProvider.configureClock(Clock.fixed(now.plusSeconds(601), ZoneOffset.UTC))
    val exception =
        assertFailsWith<HttpInvalidTokenException> {
          provider.getRefreshClaims(jwtDto.refreshToken)
        }

    assertTrue(
        exception.message.contains("expired", ignoreCase = true) ||
            exception.cause?.message?.contains("expired", ignoreCase = true) == true,
    )
  }

  @Test
  fun `getExpiredClaims should fail for refresh token`() {
    val jwtDto = provider.createJwtDto(id = "123", subject = "USER")
    assertFailsWith<HttpInvalidTokenException> { provider.getExpiredClaims(jwtDto.refreshToken) }
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
    val expectedAccessExp = fixedNow.plusSeconds(60)
    val expectedRefreshExp = fixedNow.plusSeconds(600)
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
      assertEquals(fixedNow, accessClaims.issuedAt)
      assertEquals(expectedAccessExp, accessClaims.expiresAt)

      val refreshClaims = provider.getRefreshClaims(jwtDto.refreshToken)
      assertEquals(fixedNow, refreshClaims.issuedAt)
      assertEquals(expectedRefreshExp, refreshClaims.expiresAt)
    }

    assertEquals(results[0].accessToken, results[1].accessToken)
    assertEquals(results[1].accessToken, results[2].accessToken)
    assertEquals(results[0].refreshToken, results[1].refreshToken)
    assertEquals(results[1].refreshToken, results[2].refreshToken)
  }

  @Test
  fun `provider should accept previous access key token`() {
    val rotatingProvider = rotatingProvider()
    val token =
        createToken(
            id = "123",
            subject = "USER",
            key = "c".repeat(80),
            keyId = "access-v1",
            expiresAt = timeProvider.nowInstant().plusSeconds(60),
        )

    val claims = rotatingProvider.getAccessClaims(token)

    assertEquals("123", claims.id)
    assertEquals("USER", claims.subject)
  }

  @Test
  fun `provider should accept previous refresh key token`() {
    val rotatingProvider = rotatingProvider()
    val token =
        createToken(
            id = "123",
            subject = "USER",
            key = "d".repeat(80),
            keyId = "refresh-v1",
            expiresAt = timeProvider.nowInstant().plusSeconds(600),
        )

    val claims = rotatingProvider.getRefreshClaims(token)

    assertEquals("123", claims.id)
    assertEquals("USER", claims.subject)
  }

  @Test
  fun `expired claims should accept previous access key token`() {
    val rotatingProvider = rotatingProvider()
    val now = Instant.parse("2026-02-24T00:00:00Z")
    timeProvider.configureClock(Clock.fixed(now, ZoneOffset.UTC))
    val token =
        createToken(
            id = "123",
            subject = "USER",
            key = "c".repeat(80),
            keyId = "access-v1",
            expiresAt = now.plusSeconds(60),
        )

    timeProvider.configureClock(Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC))
    val claims = rotatingProvider.getExpiredClaims(token)

    assertEquals("123", claims.id)
    assertEquals("USER", claims.subject)
  }

  @Test
  fun `provider should reject unknown kid`() {
    val rotatingProvider = rotatingProvider()
    val token =
        createToken(
            id = "123",
            subject = "USER",
            key = "c".repeat(80),
            keyId = "unknown-kid",
            expiresAt = timeProvider.nowInstant().plusSeconds(60),
        )

    assertFailsWith<HttpInvalidTokenException> { rotatingProvider.getAccessClaims(token) }
  }

  @Test
  fun `provider should accept legacy token without kid`() {
    val rotatingProvider = rotatingProvider()
    val token =
        createToken(
            id = "123",
            subject = "USER",
            key = "c".repeat(80),
            keyId = null,
            expiresAt = timeProvider.nowInstant().plusSeconds(60),
        )

    val claims = rotatingProvider.getAccessClaims(token)

    assertEquals("123", claims.id)
    assertEquals("USER", claims.subject)
  }

  private fun rotatingProvider(): JwtProvider =
      JwtProvider(
          accessKey = "a".repeat(80),
          refreshKey = "b".repeat(80),
          accessKeyId = "access-v2",
          refreshKeyId = "refresh-v2",
          previousAccessKeys = mapOf("access-v1" to "c".repeat(80)),
          previousRefreshKeys = mapOf("refresh-v1" to "d".repeat(80)),
          accessExpiredSecond = 60,
          refreshExpiredSecond = 600,
          timeProvider = timeProvider,
      )

  private fun createToken(
      id: String,
      subject: String,
      key: String,
      keyId: String?,
      expiresAt: Instant,
  ): String {
    val normalizedKey = Base64.getEncoder().encode(key.toByteArray(StandardCharsets.UTF_8))
    val header =
        JWSHeader.Builder(JWSAlgorithm.HS512)
            .type(JOSEObjectType.JWT)
            .apply { if (keyId != null) keyID(keyId) }
            .build()
    val claims =
        JWTClaimsSet.Builder()
            .jwtID(id)
            .issuer("InfosungAtomic")
            .subject(subject)
            .issueTime(Date.from(timeProvider.nowInstant()))
            .expirationTime(Date.from(expiresAt))
            .claim("service_name", "InfosungAtomic")
            .build()
    return SignedJWT(header, claims).apply { sign(MACSigner(normalizedKey)) }.serialize()
  }
}
