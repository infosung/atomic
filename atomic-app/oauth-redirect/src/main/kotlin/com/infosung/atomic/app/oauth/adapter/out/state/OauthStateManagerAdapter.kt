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
    val jwt =
        oauthStateManager.verifyState(
            signedState = signedState,
            expectedProvider = expectedProvider,
        )
    val provider =
        jwt.claims["provider"]?.toString()?.let {
          runCatching { OauthProviderName.valueOf(it) }.getOrNull()
        }
    @Suppress("UNCHECKED_CAST")
    val attributes =
        (jwt.claims["attributes"] as? Map<*, *>)?.entries?.associate { (key, value) ->
          key.toString() to value.toString()
        } ?: emptyMap()
    val verifiedState =
        OauthVerifiedState(
            provider = provider,
            redirectUri = jwt.claims["redirect_uri"]?.toString(),
            nonce = jwt.claims["nonce"]?.toString(),
            attributes = attributes,
        )
    log.debug(
        "Translated oauth state jwt into verified-state model: expectedProvider={}, resolvedProvider={}, hasRedirectUri={}, hasNonce={}, attributesCount={}",
        expectedProvider,
        verifiedState.provider,
        !verifiedState.redirectUri.isNullOrBlank(),
        !verifiedState.nonce.isNullOrBlank(),
        verifiedState.attributes.size,
    )
    return verifiedState
  }
}
