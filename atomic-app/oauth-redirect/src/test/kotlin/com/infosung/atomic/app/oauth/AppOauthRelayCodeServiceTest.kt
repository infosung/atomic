package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthProviderName
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
}
