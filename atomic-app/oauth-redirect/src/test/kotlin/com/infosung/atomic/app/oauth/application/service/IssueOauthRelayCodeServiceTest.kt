package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.port.out.StoreOauthRelayCodePort
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IssueOauthRelayCodeServiceTest {
  @Test
  fun `issue should save relay payload with configured expiry`() {
    val now = Instant.parse("2026-03-19T00:00:00Z")
    val timeProvider = TimeProvider(Clock.fixed(now, ZoneOffset.UTC))
    val storePort = CapturingStoreOauthRelayCodePort()
    val service =
        IssueOauthRelayCodeService(
            storeOauthRelayCodePort = storePort,
            properties = AtomicAppOauthRedirectProperties().apply { relayCodeTtlSeconds = 180 },
            timeProvider = timeProvider,
        )
    val payload =
        OauthRelayPayload(
            provider = OauthProviderName.GOOGLE,
            accessToken = "access-token",
        )

    val relayCode = service.issue(payload)

    assertTrue(relayCode.isNotBlank())
    assertEquals(relayCode, storePort.savedRelayCode)
    assertEquals(payload, storePort.savedPayload)
    assertEquals(now.plusSeconds(180), storePort.savedExpiresAt)
  }

  @Test
  fun `issue should reject non positive relay code ttl`() {
    val service =
        IssueOauthRelayCodeService(
            storeOauthRelayCodePort = CapturingStoreOauthRelayCodePort(),
            properties = AtomicAppOauthRedirectProperties().apply { relayCodeTtlSeconds = 0 },
        )

    val exception =
        assertFailsWith<IllegalArgumentException> {
          service.issue(OauthRelayPayload(provider = OauthProviderName.APPLE))
        }

    assertEquals(
        "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero.",
        exception.message,
    )
  }

  private class CapturingStoreOauthRelayCodePort : StoreOauthRelayCodePort {
    var savedRelayCode: String? = null
    var savedPayload: OauthRelayPayload? = null
    var savedExpiresAt: Instant? = null

    override fun save(relayCode: String, payload: OauthRelayPayload, expiresAt: Instant) {
      savedRelayCode = relayCode
      savedPayload = payload
      savedExpiresAt = expiresAt
    }

    override fun pop(relayCode: String, now: Instant): OauthRelayPayload? = null
  }
}
