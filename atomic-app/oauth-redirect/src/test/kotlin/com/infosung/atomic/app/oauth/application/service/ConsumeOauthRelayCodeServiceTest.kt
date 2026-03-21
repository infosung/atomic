package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeRequestException
import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConsumeOauthRelayCodeServiceTest {
  @Test
  fun `consume should trim relay code and return stored payload`() {
    val now = Instant.parse("2026-03-19T00:00:00Z")
    val timeProvider = TimeProvider(Clock.fixed(now, ZoneOffset.UTC))
    val payload = OauthRelayPayload(provider = OauthProviderName.KAKAO, idToken = "id-token")
    val storePort = CapturingStoreOauthRelayCodePort(payload)
    val service =
        ConsumeOauthRelayCodeService(
            storeOauthRelayCodePort = storePort,
            timeProvider = timeProvider,
        )

    val consumed = service.consume("  relay-code-1  ")

    assertEquals(payload, consumed)
    assertEquals("relay-code-1", storePort.lastRelayCode)
    assertEquals(now, storePort.lastNow)
  }

  @Test
  fun `consume should reject blank relay code`() {
    val service =
        ConsumeOauthRelayCodeService(
            storeOauthRelayCodePort = CapturingStoreOauthRelayCodePort(null),
        )

    val exception = assertFailsWith<OauthRelayCodeRequestException> { service.consume("   ") }

    assertEquals("relayCode is required.", exception.message)
  }

  @Test
  fun `consume should reject unknown expired or already used relay code`() {
    val service =
        ConsumeOauthRelayCodeService(
            storeOauthRelayCodePort = CapturingStoreOauthRelayCodePort(null),
        )

    val exception =
        assertFailsWith<OauthRelayCodeRequestException> { service.consume("relay-code-2") }

    assertEquals("relayCode is invalid, expired, or already used.", exception.message)
  }

  private class CapturingStoreOauthRelayCodePort(
      private val payload: OauthRelayPayload?,
  ) : StoreOauthRelayCodePort {
    var lastRelayCode: String? = null
    var lastNow: Instant? = null

    override fun save(relayCode: String, payload: OauthRelayPayload, expiresAt: Instant) = Unit

    override fun pop(relayCode: String, now: Instant): OauthRelayPayload? {
      lastRelayCode = relayCode
      lastNow = now
      return payload
    }
  }
}
