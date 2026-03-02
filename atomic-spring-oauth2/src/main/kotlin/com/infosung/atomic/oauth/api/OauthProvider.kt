package com.infosung.atomic.oauth.api

/**
 * Unified OAuth provider contract.
 */
interface OauthProvider {
  val providerName: OauthProviderName

  /**
   * Declares supported provider capabilities.
   */
  fun capabilities(): Set<OauthProviderCapability>

  /**
   * Builds provider authorization URL.
   */
  fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String

  /**
   * Exchanges authorization code for token set.
   */
  fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult

  /**
   * Refreshes access token using refresh token.
   */
  fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult

  /**
   * Revokes token at provider.
   */
  fun revokeToken(request: OauthTokenRevokeRequest)

  /**
   * Resolves user identity from id-token and/or userinfo API.
   */
  fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult

  /**
   * Returns true when [capability] is supported.
   */
  fun supports(capability: OauthProviderCapability): Boolean = capabilities().contains(capability)
}
