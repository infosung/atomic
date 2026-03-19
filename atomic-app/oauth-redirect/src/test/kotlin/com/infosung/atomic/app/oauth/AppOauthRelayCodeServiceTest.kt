package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeRequestException
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AppOauthRelayCodeServiceTest {
  @Test
  fun `issue and consume should return stored payload`() {
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val payload =
        OauthRelayPayload(
            provider = OauthProviderName.GOOGLE,
            idToken = "id-token",
        )

    val relayCode = service.issueRelayCode(payload)
    val consumed = service.consumeRelayCode(relayCode)

    assertNotNull(consumed)
    assertEquals(OauthProviderName.GOOGLE, consumed.provider)
    assertEquals("id-token", consumed.idToken)
  }

  @Test
  fun `consume should fail when relayCode already used`() {
    val properties = AtomicAppOauthRedirectProperties()
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = properties,
        )
    val payload =
        OauthRelayPayload(
            provider = OauthProviderName.KAKAO,
            accessToken = "access-token",
        )
    val relayCode = service.issueRelayCode(payload)
    service.consumeRelayCode(relayCode)

    val exception = assertFailsWith<HttpStatusException> { service.consumeRelayCode(relayCode) }

    assertEquals(400, exception.status)
  }

  @Test
  fun `consume should fail when relayCode is blank`() {
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
        )

    val exception = assertFailsWith<HttpStatusException> { service.consumeRelayCode("   ") }

    assertEquals(400, exception.status)
    assertEquals("relayCode is required.", exception.message)
  }

  @Test
  fun `consume should fail when relayCode is expired`() {
    val timeProvider =
        TimeProvider(Clock.fixed(Instant.parse("2026-03-14T00:00:00Z"), ZoneOffset.UTC))
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(timeProvider = timeProvider),
            properties = AtomicAppOauthRedirectProperties().apply { relayCodeTtlSeconds = 60 },
            timeProvider = timeProvider,
        )
    val payload = OauthRelayPayload(provider = OauthProviderName.GOOGLE)
    val relayCode = service.issueRelayCode(payload)

    timeProvider.configureClock(Clock.fixed(Instant.parse("2026-03-14T00:02:00Z"), ZoneOffset.UTC))

    val exception = assertFailsWith<HttpStatusException> { service.consumeRelayCode(relayCode) }

    assertEquals(400, exception.status)
    assertEquals("relayCode is invalid, expired, or already used.", exception.message)
  }

  @Test
  fun `issue should fail when relayCode ttl is non positive`() {
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties().apply { relayCodeTtlSeconds = 0 },
        )

    val exception =
        assertFailsWith<IllegalArgumentException> {
          service.issueRelayCode(OauthRelayPayload(provider = OauthProviderName.APPLE))
        }

    assertEquals(
        "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero.",
        exception.message,
    )
  }

  @Test
  fun `consume should translate relay application exception to documented http status`() {
    val service =
        AppOauthRelayCodeService(
            relayCodeStore = InMemoryOauthRelayCodeStore(),
            properties = AtomicAppOauthRedirectProperties(),
            issueOauthRelayCodeUseCase = StubIssueOauthRelayCodeUseCase(),
            consumeOauthRelayCodeUseCase =
                ConsumeOauthRelayCodeUseCase {
                  throw OauthRelayCodeRequestException(
                      "relayCode is invalid, expired, or already used.",
                  )
                },
        )

    val exception = assertFailsWith<HttpStatusException> { service.consumeRelayCode("relay-1") }

    assertEquals(400, exception.status)
    assertEquals("relayCode is invalid, expired, or already used.", exception.message)
  }

  private class StubIssueOauthRelayCodeUseCase : IssueOauthRelayCodeUseCase {
    override fun issue(payload: OauthRelayPayload): String = "relay-code"
  }
}
