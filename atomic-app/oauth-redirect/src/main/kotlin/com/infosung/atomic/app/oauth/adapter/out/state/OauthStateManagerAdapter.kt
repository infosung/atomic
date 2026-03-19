package com.infosung.atomic.app.oauth.adapter.out.state

import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.state.OauthStateManager
import org.slf4j.LoggerFactory

internal class OauthStateManagerAdapter(
    private val oauthStateManager: OauthStateManager,
) : VerifyOauthStatePort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun verifyState(
      signedState: String,
      expectedProvider: OauthProviderName,
  ): OauthVerifiedState {
    val stateClaims =
        oauthStateManager.verifyStateClaims(
            signedState = signedState,
            expectedProvider = expectedProvider,
        )
    val verifiedState =
        OauthVerifiedState(
            provider = stateClaims.provider,
            redirectUri = stateClaims.redirectUri,
            nonce = stateClaims.nonce,
            attributes = stateClaims.attributes,
        )
    log.debug(
        "Translated typed oauth state claims into verified-state model: expectedProvider={}, resolvedProvider={}, hasRedirectUri={}, hasNonce={}, attributesCount={}",
        expectedProvider,
        verifiedState.provider,
        !verifiedState.redirectUri.isNullOrBlank(),
        !verifiedState.nonce.isNullOrBlank(),
        verifiedState.attributes.size,
    )
    return verifiedState
  }
}
