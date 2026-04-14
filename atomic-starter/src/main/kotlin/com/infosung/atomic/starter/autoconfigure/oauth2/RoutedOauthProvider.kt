package com.infosung.atomic.starter.autoconfigure.oauth2

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.exception.HttpJwtVerifyException
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.state.OauthStateManager
import org.slf4j.LoggerFactory

/** Provider router for one OAuth provider name with multiple platform-specific clients. */
internal class RoutedOauthProvider(
    override val providerName: OauthProviderName,
    providersByClientKey: Map<String, OauthProvider>,
    private val defaultClientKey: String,
    private val routeAttributeKey: String,
    private val oauthStateManager: OauthStateManager,
    private val audiencesByClientKey: Map<String, Set<String>> = emptyMap(),
) : OauthProvider {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val providersByClientKey: Map<String, OauthProvider> =
      providersByClientKey.toMap().also { providers ->
        require(providers.isNotEmpty()) { "providersByClientKey must not be empty." }
        require(providers.containsKey(defaultClientKey)) {
          "defaultClientKey '$defaultClientKey' is not registered for provider $providerName"
        }
      }

  private val capabilitySet: Set<OauthProviderCapability> =
      this.providersByClientKey.values.first().capabilities().also { baseCapabilitySet ->
        require(this.providersByClientKey.values.all { it.capabilities() == baseCapabilitySet }) {
          "All routed oauth providers for $providerName must expose identical capability sets."
        }
      }

  override fun capabilities(): Set<OauthProviderCapability> = capabilitySet

  override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
    val requestedClientKey =
        request.stateAttributes[routeAttributeKey]
            ?: request.additionalParameters[routeAttributeKey]
    val clientKey = resolveClientKeyOrDefault(requestedClientKey)

    val routedRequest =
        request.copy(
            stateAttributes = request.stateAttributes + mapOf(routeAttributeKey to clientKey),
            additionalParameters = request.additionalParameters - routeAttributeKey,
        )
    return delegate(clientKey).buildAuthorizationUrl(routedRequest)
  }

  override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
    val requestClientKey = request.additionalParameters[routeAttributeKey]
    val clientKey =
        if (providersByClientKey.size > 1) {
          val keyFromState = requireClientKeyFromStateForExchange(request.state)
          if (!requestClientKey.isNullOrBlank() && requestClientKey != keyFromState) {
            throw InvalidOauthRequestException(
                "Oauth client routing mismatch: state=$keyFromState, request=$requestClientKey",
            )
          }
          keyFromState
        } else {
          val keyFromState = readClientKeyFromStateOrNull(request.state)
          resolveClientKeyOrDefault(keyFromState ?: requestClientKey)
        }
    val sanitizedRequest =
        request.copy(additionalParameters = request.additionalParameters - routeAttributeKey)
    return delegate(clientKey).exchangeCode(sanitizedRequest)
  }

  override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
    val clientKey = resolveClientKeyOrDefault(request.additionalParameters[routeAttributeKey])
    val sanitizedRequest =
        request.copy(additionalParameters = request.additionalParameters - routeAttributeKey)
    return delegate(clientKey).refreshToken(sanitizedRequest)
  }

  override fun revokeToken(request: OauthTokenRevokeRequest) {
    val clientKey = resolveClientKeyOrDefault(request.additionalParameters[routeAttributeKey])
    val sanitizedRequest =
        request.copy(additionalParameters = request.additionalParameters - routeAttributeKey)
    delegate(clientKey).revokeToken(sanitizedRequest)
  }

  override fun resolveIdentity(
      request: OauthIdentityRequest
  ): com.infosung.atomic.oauth.api.OauthIdentityResult {
    val requestedClientKey = request.additionalParameters[routeAttributeKey]
    val sanitizedRequest =
        request.copy(additionalParameters = request.additionalParameters - routeAttributeKey)

    if (!requestedClientKey.isNullOrBlank()) {
      val clientKey = resolveClientKeyOrDefault(requestedClientKey)
      return stampSelectedClientKey(
          delegate(clientKey).resolveIdentity(sanitizedRequest), clientKey)
    }

    val audience = request.audience?.takeIf { it.isNotBlank() }
    if (audience != null) {
      val matchedClientKey =
          audiencesByClientKey.entries
              .firstOrNull { (_, audiences) -> audiences.contains(audience) }
              ?.key
      if (matchedClientKey != null) {
        return stampSelectedClientKey(
            delegate(matchedClientKey).resolveIdentity(sanitizedRequest), matchedClientKey)
      }
    }

    if ((request.strategy == OauthIdentityStrategy.AUTO ||
        request.strategy == OauthIdentityStrategy.ID_TOKEN) && !request.idToken.isNullOrBlank()) {
      var lastRoutingError: Exception? = null
      for ((clientKey, provider) in providersByClientKey.toSortedMap()) {
        try {
          return stampSelectedClientKey(provider.resolveIdentity(sanitizedRequest), clientKey)
        } catch (e: HttpJwtVerifyException) {
          lastRoutingError =
              InvalidOauthRequestException(
                  "ID token is not valid for routed oauth client '$clientKey'.",
                  e,
              )
        } catch (e: InvalidOauthRequestException) {
          lastRoutingError = e
        } catch (e: Exception) {
          throw e
        }
      }
      if (lastRoutingError != null) {
        throw lastRoutingError
      }
    }

    return stampSelectedClientKey(
        delegate(defaultClientKey).resolveIdentity(sanitizedRequest), defaultClientKey)
  }

  private fun requireClientKeyFromStateForExchange(state: String): String {
    val stateClaims =
        oauthStateManager.readStateClaims(
            signedState = state,
            expectedProvider = providerName,
        )
    val value =
        stateClaims.attributes[routeAttributeKey]?.takeIf { it.isNotBlank() }
            ?: throw InvalidOauthRequestException(
                "OAuth state does not include required routing key '$routeAttributeKey'.",
            )
    if (!providersByClientKey.containsKey(value)) {
      throw InvalidOauthRequestException(
          "Unknown oauth client key in state for $providerName: $value",
      )
    }
    log.debug(
        "Resolved routed oauth client key from typed state claims: provider={}, routeAttributeKey={}, clientKey={}",
        providerName,
        routeAttributeKey,
        value,
    )
    return value
  }

  private fun readClientKeyFromStateOrNull(state: String): String? {
    val stateClaims =
        runCatching {
              oauthStateManager.readStateClaims(
                  signedState = state,
                  expectedProvider = providerName,
              )
            }
            .getOrNull() ?: return null
    val clientKey = stateClaims.attributes[routeAttributeKey]?.takeIf { it.isNotBlank() }
    if (clientKey != null) {
      log.debug(
          "Read optional routed oauth client key from typed state claims: provider={}, routeAttributeKey={}, clientKey={}",
          providerName,
          routeAttributeKey,
          clientKey,
      )
    }
    return clientKey
  }

  private fun resolveClientKeyOrDefault(clientKey: String?): String {
    if (clientKey.isNullOrBlank()) {
      return defaultClientKey
    }
    if (!providersByClientKey.containsKey(clientKey)) {
      throw InvalidOauthRequestException(
          "Unknown oauth client key for $providerName: $clientKey",
      )
    }
    return clientKey
  }

  private fun delegate(clientKey: String): OauthProvider {
    return providersByClientKey[clientKey]
        ?: throw InvalidOauthRequestException(
            "Unknown oauth client key for $providerName: $clientKey")
  }

  private fun stampSelectedClientKey(
      identityResult: OauthIdentityResult,
      clientKey: String,
  ): OauthIdentityResult {
    return identityResult.copy(selectedClientKey = clientKey)
  }
}
