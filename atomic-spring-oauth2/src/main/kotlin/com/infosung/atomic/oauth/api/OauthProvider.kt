package com.infosung.atomic.oauth.api

interface OauthProvider {
  val providerName: OauthProviderName

  fun capabilities(): Set<OauthProviderCapability>

  fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String

  fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult

  fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult

  fun revokeToken(request: OauthTokenRevokeRequest)

  fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult

  fun supports(capability: OauthProviderCapability): Boolean = capabilities().contains(capability)
}
