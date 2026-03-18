package com.infosung.atomic.app.oauth.adapter.out.oauth

import com.infosung.atomic.app.oauth.application.port.out.OauthProviderAuthorization
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderTokenExchange
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import org.slf4j.LoggerFactory

internal class OauthServiceProviderAdapter(
    private val oauthServiceProvider: OauthServiceProvider,
) : OauthProviderOperationsPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun requireProviderName(provider: String): OauthProviderName {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw HttpStatusException(status = 400, message = "Unsupported provider: $provider")
    return oauthProvider.providerName
  }

  override fun buildAuthorizationUrl(
      provider: String,
      request: OauthAuthorizationRequest,
  ): OauthProviderAuthorization {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw HttpStatusException(status = 400, message = "Unsupported provider: $provider")
    val authorizationUrl = oauthProvider.buildAuthorizationUrl(request)
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
            ?: throw HttpStatusException(status = 400, message = "Unsupported provider: $provider")
    val tokenResult = oauthProvider.exchangeCode(request)
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
