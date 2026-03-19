package com.infosung.atomic.oauth.api

import com.infosung.atomic.oauth.exception.OauthException
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OauthServiceProviderTest {
  @Test
  fun `provider lookup should be locale-safe and case-insensitive`() {
    val googleProvider = StubOauthProvider(OauthProviderName.GOOGLE)
    val provider = OauthServiceProvider(listOf(googleProvider))

    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    try {
      assertSame(googleProvider, provider.getService("google"))
      assertSame(googleProvider, provider.getService("GoOgLe"))
      assertSame(googleProvider, provider.getService(OauthProviderName.GOOGLE))
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun `provider lookup should return null for invalid or unknown type`() {
    val provider = OauthServiceProvider(listOf(StubOauthProvider(OauthProviderName.GOOGLE)))

    assertNull(provider.getService(""))
    assertNull(provider.getService("not-supported"))
    assertNull(provider.getService(OauthProviderName.KAKAO))
  }

  @Test
  fun `requireService should throw when provider is missing`() {
    val provider = OauthServiceProvider(listOf(StubOauthProvider(OauthProviderName.GOOGLE)))

    val exception =
        assertFailsWith<OauthException> { provider.requireService(OauthProviderName.KAKAO) }
    assertNotNull(exception.message)
    assertEquals(true, exception.message!!.contains("KAKAO"))
  }

  @Test
  fun `provider registry should fail fast when duplicate provider names are registered`() {
    val firstGoogle = StubOauthProvider(OauthProviderName.GOOGLE)
    val secondGoogle = StubOauthProvider(OauthProviderName.GOOGLE)

    val exception =
        assertFailsWith<IllegalArgumentException> {
          OauthServiceProvider(listOf(firstGoogle, secondGoogle))
        }

    assertNotNull(exception.message)
    assertTrue(exception.message!!.contains("GOOGLE"))
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
      return OauthIdentityResult(provider = providerName, userId = "stub")
    }
  }
}
