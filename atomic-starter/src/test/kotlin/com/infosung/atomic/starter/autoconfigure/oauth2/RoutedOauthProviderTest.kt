package com.infosung.atomic.starter.autoconfigure.oauth2

import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertSame
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class RoutedOauthProviderTest {
  @Test
  fun `exchangeCode should route with typed state claims instead of raw jwt claims`() {
    val oauthStateManager = mock(OauthStateManager::class.java)
    val webProvider = mock(OauthProvider::class.java)
    val mobileProvider = mock(OauthProvider::class.java)
    val expected =
        OauthTokenResult(
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
    `when`(webProvider.capabilities()).thenReturn(setOf(OauthProviderCapability.EXCHANGE_TOKEN))
    `when`(mobileProvider.capabilities()).thenReturn(setOf(OauthProviderCapability.EXCHANGE_TOKEN))
    val routedProvider =
        RoutedOauthProvider(
            providerName = OauthProviderName.GOOGLE,
            providersByClientKey = mapOf("web" to webProvider, "mobile" to mobileProvider),
            defaultClientKey = "web",
            routeAttributeKey = "clientKey",
            oauthStateManager = oauthStateManager,
        )
    `when`(
            oauthStateManager.readStateClaims(
                "signed-state",
                OauthProviderName.GOOGLE,
                null,
                null,
            ),
        )
        .thenReturn(
            OauthStateClaims(
                issuer = "atomic-test",
                stateId = "state-1",
                issuedAt = Instant.parse("2026-03-19T00:00:00Z"),
                expiresAt = Instant.parse("2026-03-19T00:05:00Z"),
                provider = OauthProviderName.GOOGLE,
                attributes = mapOf("clientKey" to "mobile"),
            ),
        )
    `when`(
            mobileProvider.exchangeCode(
                OauthTokenExchangeRequest(
                    code = "code-value",
                    state = "signed-state",
                ),
            ),
        )
        .thenReturn(expected)

    val actual =
        routedProvider.exchangeCode(
            OauthTokenExchangeRequest(
                code = "code-value",
                state = "signed-state",
            ),
        )

    assertSame(expected, actual)
    verify(oauthStateManager).readStateClaims("signed-state", OauthProviderName.GOOGLE, null, null)
    verify(oauthStateManager, never())
        .readState("signed-state", OauthProviderName.GOOGLE, null, null)
    verify(mobileProvider)
        .exchangeCode(
            OauthTokenExchangeRequest(
                code = "code-value",
                state = "signed-state",
            ),
        )
  }
}
