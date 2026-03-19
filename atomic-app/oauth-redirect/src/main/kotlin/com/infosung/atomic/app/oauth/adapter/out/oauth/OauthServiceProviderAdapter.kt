package com.infosung.atomic.app.oauth.adapter.out.oauth

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderAuthorization
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderTokenExchange
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.exception.OauthException
import org.slf4j.LoggerFactory

internal class OauthServiceProviderAdapter(
    private val oauthServiceProvider: OauthServiceProvider,
) : OauthProviderOperationsPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun requireProviderName(provider: String): OauthProviderName {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw OauthRedirectRequestException("Unsupported provider: $provider")
    return oauthProvider.providerName
  }

  override fun buildAuthorizationUrl(
      provider: String,
      request: OauthAuthorizationRequest,
  ): OauthProviderAuthorization {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw OauthRedirectRequestException("Unsupported provider: $provider")
    val authorizationUrl =
        try {
          oauthProvider.buildAuthorizationUrl(request)
        } catch (e: OauthException) {
          throw OauthRedirectRequestException(
              e.message ?: "Invalid OAuth authorization request for provider: $provider",
              e,
          )
        } catch (e: IllegalArgumentException) {
          throw OauthRedirectRequestException(
              e.message ?: "Invalid OAuth authorization request for provider: $provider",
              e,
          )
        }
    log.trace(
        "Resolved oauth authorization provider through adapter: provider={}, redirectUri={}",
        oauthProvider.providerName,
        request.redirectUri,
    )
    return OauthProviderAuthorization(
        providerName = oauthProvider.providerName,
        authorizationUrl = authorizationUrl,
    )
  }

  override fun exchangeCode(
      provider: String,
      request: OauthTokenExchangeRequest,
  ): OauthProviderTokenExchange {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw OauthRedirectRequestException("Unsupported provider: $provider")
    val tokenResult =
        try {
          oauthProvider.exchangeCode(request)
        } catch (e: OauthException) {
          throw OauthRedirectRequestException(
              e.message ?: "Invalid OAuth callback request for provider: $provider",
              e,
          )
        } catch (e: IllegalArgumentException) {
          throw OauthRedirectRequestException(
              e.message ?: "Invalid OAuth callback request for provider: $provider",
              e,
          )
        }
    log.trace(
        "Resolved oauth token exchange through adapter: provider={}, additionalParameterKeys={}",
        oauthProvider.providerName,
        request.additionalParameters.keys.sorted(),
    )
    return OauthProviderTokenExchange(
        providerName = oauthProvider.providerName,
        tokenResult = tokenResult,
    )
  }
}
