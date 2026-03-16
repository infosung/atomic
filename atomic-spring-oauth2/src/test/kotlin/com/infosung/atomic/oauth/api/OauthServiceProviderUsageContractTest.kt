package com.infosung.atomic.oauth.api

import com.infosung.atomic.oauth.exception.OauthException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class OauthServiceProviderUsageContractTest {
  @Test
  fun `string lookup should be locale-safe and case-insensitive for consumer routing`() {
    val googleProvider = StubOauthProvider(OauthProviderName.GOOGLE)
    val kakaoProvider = StubOauthProvider(OauthProviderName.KAKAO)
    val provider = OauthServiceProvider(listOf(googleProvider, kakaoProvider))

    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    try {
      assertSame(googleProvider, provider.getService("google"))
      assertSame(googleProvider, provider.getService("GoOgLe"))
      assertSame(kakaoProvider, provider.getService("KAKAO"))
      assertSame(googleProvider, provider.getService(OauthProviderName.GOOGLE))
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun `missing provider should return null for optional lookup and descriptive exception for required lookup`() {
    val provider = OauthServiceProvider(listOf(StubOauthProvider(OauthProviderName.GOOGLE)))

    assertNull(provider.getService("not-supported"))
    assertNull(provider.getService(OauthProviderName.KAKAO))

    val exception =
        assertFailsWith<OauthException> { provider.requireService(OauthProviderName.APPLE) }
    assertEquals("OAuth provider is not registered: APPLE", exception.message)
  }

  private class StubOauthProvider(
      override val providerName: OauthProviderName,
  ) : OauthProvider {
    override fun capabilities(): Set<OauthProviderCapability> = emptySet()

    override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String = ""

    override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult =
        OauthTokenResult()

    override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult =
        OauthTokenResult()

    override fun revokeToken(request: OauthTokenRevokeRequest) = Unit

    override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
      return OauthIdentityResult(provider = providerName, userId = "user-1")
    }
  }
}
