package com.infosung.atomic.app.oauth.application.port.out

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult

internal interface OauthProviderOperationsPort {
  fun requireProviderName(provider: String): OauthProviderName {
    throw UnsupportedOperationException("requireProviderName is not implemented.")
  }

  fun buildAuthorizationUrl(
      provider: String,
      request: OauthAuthorizationRequest,
  ): OauthProviderAuthorization {
    throw UnsupportedOperationException("buildAuthorizationUrl is not implemented.")
  }

  fun exchangeCode(
      provider: String,
      request: OauthTokenExchangeRequest,
  ): OauthProviderTokenExchange {
    throw UnsupportedOperationException("exchangeCode is not implemented.")
  }
}

internal data class OauthProviderAuthorization(
    val providerName: OauthProviderName,
    val authorizationUrl: String,
)

internal data class OauthProviderTokenExchange(
    val providerName: OauthProviderName,
    val tokenResult: OauthTokenResult,
)
