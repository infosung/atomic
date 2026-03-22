package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object OauthRedirectUseCaseSupport {
  fun buildCallbackBindingStateAttributes(
      callbackBindingEnabled: Boolean,
      callbackBindingStateAttributeKey: String,
      callbackBindingToken: String?,
  ): Map<String, String> {
    if (!callbackBindingEnabled) {
      return emptyMap()
    }
    val bindingToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding token is required.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    return mapOf(callbackBindingStateAttributeKey to bindingToken)
  }

  fun validateCallbackBinding(
      verifiedState: OauthVerifiedState,
      callbackBindingEnabled: Boolean,
      callbackBindingStateAttributeKey: String,
      callbackBindingToken: String?,
  ) {
    if (!callbackBindingEnabled) {
      return
    }
    val expectedToken =
        verifiedState.attributes[callbackBindingStateAttributeKey]?.trim()?.takeIf {
          it.isNotBlank()
        }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding state is missing.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    val actualToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding cookie is missing.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    if (actualToken != expectedToken) {
      throw OauthRedirectRequestException(
          message = "OAuth callback binding token mismatch.",
          errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
      )
    }
  }

  fun readRedirectUri(verifiedState: OauthVerifiedState): String {
    return verifiedState.redirectUri
        ?: throw OauthRedirectRequestException(
            message = "State does not include redirect_uri.",
            errorCode = OauthRedirectErrorCode.OAUTH_STATE_INVALID,
        )
  }

  fun resolveRelayCodeQueryParameterName(relayCodeQueryParameterName: String): String {
    val key = relayCodeQueryParameterName.trim()
    if (key.isBlank()) {
      throw IllegalStateException(
          "atomic.app.oauth.redirect.relay-code-query-parameter-name must not be blank.",
      )
    }
    return key
  }

  fun appendQueryParameter(
      url: String,
      key: String,
      value: String,
  ): String {
    val fragmentIndex = url.indexOf('#')
    val baseUrl = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
    val fragment = if (fragmentIndex >= 0) url.substring(fragmentIndex) else ""
    val separator = if (baseUrl.contains('?')) "&" else "?"
    val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
    val encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8)
    return "$baseUrl$separator$encodedKey=$encodedValue$fragment"
  }

  fun toRelayPayload(
      provider: OauthProviderName,
      tokenResult: OauthTokenResult,
      verifiedState: OauthVerifiedState,
  ): OauthRelayPayload {
    return OauthRelayPayload(
        provider = provider,
        accessToken = tokenResult.accessToken,
        refreshToken = tokenResult.refreshToken,
        idToken = tokenResult.idToken,
        tokenType = tokenResult.tokenType,
        expiresInSeconds = tokenResult.expiresInSeconds,
        scopes = tokenResult.scopes,
        raw = tokenResult.raw,
        nonce = verifiedState.nonce,
        stateAttributes = verifiedState.attributes,
    )
  }
}
