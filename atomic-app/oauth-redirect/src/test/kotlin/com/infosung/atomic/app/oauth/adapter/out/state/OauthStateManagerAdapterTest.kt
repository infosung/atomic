package com.infosung.atomic.app.oauth.adapter.out.state

import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.state.OauthStateClaims
import com.infosung.atomic.oauth.state.OauthStateManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class OauthStateManagerAdapterTest {
  @Test
  fun `verifyState should translate typed state claims into verified state model`() {
    val oauthStateManager = mock(OauthStateManager::class.java)
    val adapter = OauthStateManagerAdapter(oauthStateManager)
    val stateClaims =
        OauthStateClaims(
            issuer = "atomic-test",
            stateId = "state-1",
            issuedAt = Instant.parse("2026-03-19T00:00:00Z"),
            expiresAt = Instant.parse("2026-03-19T00:05:00Z"),
            provider = OauthProviderName.GOOGLE,
            redirectUri = "myapp://oauth/callback",
            nonce = "nonce-1",
            attributes = mapOf("binding" to "token-1"),
        )
    `when`(
            oauthStateManager.verifyStateClaims(
                "signed-state", OauthProviderName.GOOGLE, null, null))
        .thenReturn(stateClaims)

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
