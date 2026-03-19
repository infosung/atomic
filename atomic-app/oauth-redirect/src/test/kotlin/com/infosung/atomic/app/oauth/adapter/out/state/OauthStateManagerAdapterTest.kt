package com.infosung.atomic.app.oauth.adapter.out.state

import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.state.OauthStateManager
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.oauth2.jwt.Jwt

class OauthStateManagerAdapterTest {
  @Test
  fun `verifyState should translate jwt claims into verified state model`() {
    val oauthStateManager = mock(OauthStateManager::class.java)
    val adapter = OauthStateManagerAdapter(oauthStateManager)
    val jwt =
        Jwt.withTokenValue("state-jwt")
            .header("alg", "HS256")
            .claim("provider", "GOOGLE")
            .claim("redirect_uri", "myapp://oauth/callback")
            .claim("nonce", "nonce-1")
            .claim("attributes", mapOf("binding" to "token-1"))
            .build()
    `when`(oauthStateManager.verifyState("signed-state", OauthProviderName.GOOGLE, null, null))
        .thenReturn(jwt)

    val verified =
        adapter.verifyState(
            signedState = "signed-state",
            expectedProvider = OauthProviderName.GOOGLE,
        )

    assertEquals(
        OauthVerifiedState(
            provider = OauthProviderName.GOOGLE,
            redirectUri = "myapp://oauth/callback",
            nonce = "nonce-1",
            attributes = mapOf("binding" to "token-1"),
        ),
        verified,
    )
  }
}
