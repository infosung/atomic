package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [AppOauthRedirectBootSmokeContractTest.TestApplication::class],
    properties =
        [
            "atomic.app.oauth.redirect.redirect-endpoint-path=/test/oauth/redirect",
            "atomic.app.oauth.redirect.enabled=true",
            "atomic.app.oauth.redirect.store.type=in_memory",
            "atomic.app.oauth.redirect.allowed-redirect-uri-prefixes=https://client.example.com",
        ],
)
@AutoConfigureMockMvc
class AppOauthRedirectBootSmokeContractTest {
  @jakarta.annotation.Resource private lateinit var mockMvc: MockMvc

  @Test
  fun `boot mvc should expose redirect endpoint with callback binding cookie`() {
    mockMvc
        .perform(
            get("/test/oauth/redirect/google")
                .queryParam("redirectUri", "https://client.example.com/callback"),
        )
        .andExpect(status().is3xxRedirection)
        .andExpect(redirectedUrl("https://provider.example.com/oauth"))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("__Host-")))
  }

  @Test
  fun `boot mvc should keep documented 400 status without custom exception advice`() {
    mockMvc
        .perform(get("/oauth/callback/apple"))
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("Apple callback supports POST form_post only."))
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
  class TestApplication {
    @Bean
    fun oauthServiceProvider(): OauthServiceProvider {
      return OauthServiceProvider(listOf(TestOauthProvider()))
    }

    @Bean
    fun oauthStateManager(): OauthStateManager {
      return OauthStateManager(
          signingSecret = "0123456789abcdef0123456789abcdef",
          store = InMemoryOauthStateStore(),
      )
    }
  }

  class TestOauthProvider : OauthProvider {
    override val providerName: OauthProviderName = OauthProviderName.GOOGLE

    override fun capabilities(): Set<OauthProviderCapability> =
        setOf(OauthProviderCapability.AUTHORIZATION_URL)

    override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String =
        "https://provider.example.com/oauth"

    override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult =
        OauthTokenResult(accessToken = "unused")

    override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult =
        OauthTokenResult()

    override fun revokeToken(request: OauthTokenRevokeRequest) = Unit

    override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
      return OauthIdentityResult(provider = providerName, userId = "user-1")
    }
  }
}
