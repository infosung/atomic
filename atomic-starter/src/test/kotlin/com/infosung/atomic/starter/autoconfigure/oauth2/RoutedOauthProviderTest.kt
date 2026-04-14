package com.infosung.atomic.starter.autoconfigure.oauth2

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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

  @Test
  fun `resolveIdentity should stamp selected client key on routed result`() {
    val oauthStateManager = mock(OauthStateManager::class.java)
    val webProvider = mock(OauthProvider::class.java)
    val mobileProvider = mock(OauthProvider::class.java)
    `when`(webProvider.capabilities())
        .thenReturn(setOf(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN))
    `when`(mobileProvider.capabilities())
        .thenReturn(setOf(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN))
    `when`(
            mobileProvider.resolveIdentity(
                OauthIdentityRequest(idToken = "id-token"),
            ),
        )
        .thenReturn(
            OauthIdentityResult(
                provider = OauthProviderName.GOOGLE,
                userId = "user-1",
                providerSubject = "user-1",
            ),
        )

    val routedProvider =
        RoutedOauthProvider(
            providerName = OauthProviderName.GOOGLE,
            providersByClientKey = mapOf("web" to webProvider, "mobile" to mobileProvider),
            defaultClientKey = "web",
            routeAttributeKey = "clientKey",
            oauthStateManager = oauthStateManager,
        )

    val actual =
        routedProvider.resolveIdentity(
            OauthIdentityRequest(
                idToken = "id-token",
                additionalParameters = mapOf("clientKey" to "mobile"),
            ),
        )

    assertEquals("mobile", actual.selectedClientKey)
    assertEquals("user-1", actual.providerSubject)
  }

  @Test
  fun `resolveIdentity should use stable client-key ordering for id token fallback`() {
    val oauthStateManager = mock(OauthStateManager::class.java)
    val aaaProvider =
        object : OauthProvider {
          override val providerName: OauthProviderName = OauthProviderName.GOOGLE

          override fun capabilities(): Set<OauthProviderCapability> =
              setOf(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN)

          override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
            throw InvalidOauthRequestException("aaa failed")
          }

          override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun revokeToken(request: OauthTokenRevokeRequest) {
            throw UnsupportedOperationException("Not used in this test")
          }
        }
    val zzzProvider =
        object : OauthProvider {
          override val providerName: OauthProviderName = OauthProviderName.GOOGLE

          override fun capabilities(): Set<OauthProviderCapability> =
              setOf(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN)

          override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
            return OauthIdentityResult(
                provider = OauthProviderName.GOOGLE,
                userId = "user-2",
                providerSubject = "user-2",
            )
          }

          override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
            throw UnsupportedOperationException("Not used in this test")
          }

          override fun revokeToken(request: OauthTokenRevokeRequest) {
            throw UnsupportedOperationException("Not used in this test")
          }
        }

    val routedProvider =
        RoutedOauthProvider(
            providerName = OauthProviderName.GOOGLE,
            providersByClientKey = linkedMapOf("zzz" to zzzProvider, "aaa" to aaaProvider),
            defaultClientKey = "zzz",
            routeAttributeKey = "clientKey",
            oauthStateManager = oauthStateManager,
        )

    val actual =
        routedProvider.resolveIdentity(
            OauthIdentityRequest(
                strategy = OauthIdentityStrategy.ID_TOKEN,
                idToken = "id-token",
            ),
        )

    assertEquals("zzz", actual.selectedClientKey)
  }
}
