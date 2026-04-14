package com.infosung.atomic.oauth.provider.apple

import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import com.infosung.atomic.oauth.api.OauthIdentityPayloadMode
import com.infosung.atomic.oauth.api.OauthIdentityRequest
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthIdentityStrategy
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderCapability
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthScopePreset
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenRefreshRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.api.OauthTokenRevokeRequest
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.exception.UnsupportedOauthOperationException
import com.infosung.atomic.oauth.idtoken.IdTokenParser
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.support.encodeQuery
import org.slf4j.LoggerFactory

/**
 * Apple OAuth provider implementation.
 *
 * This implementation builds authorization URL and resolves identity from id-token.
 */
class AppleOauthProvider(
    val clientId: String,
    val serverRedirectUri: String,
    val idTokenParser: IdTokenParser,
    private val stateManager: OauthStateManager,
    val audValidator: ((String?) -> String)? = null,
    private val defaultScopes: Set<String> = setOf("email"),
    private val requireNonceValidation: Boolean = true,
) : OauthProvider {
  private val log = LoggerFactory.getLogger(this::class.java)

  override val providerName: OauthProviderName = OauthProviderName.APPLE

  private val capabilitySet =
      setOf(
          OauthProviderCapability.AUTHORIZATION_URL,
          OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN,
          OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY,
          OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE,
          OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE,
      )

  private val authorizationReservedKeys =
      setOf("client_id", "redirect_uri", "response_type", "response_mode", "scope", "state")

  override fun capabilities(): Set<OauthProviderCapability> = capabilitySet

  override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
    requireCapability(OauthProviderCapability.AUTHORIZATION_URL)
    validatePkceUnsupported(request)
    val scopes = resolveScopes(request.scopes, request.scopePreset)
    // Client redirect target is stored in state for your callback controller logic.
    val clientRedirectUri = request.redirectUri
    val state =
        stateManager.issueState(
            provider = providerName,
            redirectUri = clientRedirectUri,
            nonce = request.nonce,
            attributes = request.stateAttributes,
        )
    log.debug("Building Apple OAuth authorization URL with scopeCount={}.", scopes.size)

    val params =
        linkedMapOf(
            "client_id" to clientId,
            // OAuth provider callback endpoint must stay as server redirect URI.
            "redirect_uri" to serverRedirectUri,
            "response_type" to "code id_token",
            "response_mode" to (request.responseMode ?: "form_post"),
            "state" to state,
        )
    if (scopes.isNotEmpty()) {
      params["scope"] = scopes.joinToString(" ")
    }
    request.prompt?.let { params["prompt"] = it }
    request.loginHint?.let { params["login_hint"] = it }
    request.nonce?.let { params["nonce"] = it }

    request.additionalParameters.forEach { (key, value) ->
      if (authorizationReservedKeys.contains(key)) {
        log.warn("Ignoring Apple auth additional parameter override for key={}.", key)
      } else {
        params[key] = value
      }
    }

    return encodeQuery("https://appleid.apple.com/auth/authorize", params)
  }

  override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
    log.info("Apple OAuth code exchange is not supported in this provider implementation.")
    throw unsupported(OauthProviderCapability.EXCHANGE_TOKEN)
  }

  override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
    log.info("Apple OAuth token refresh is not supported in this provider implementation.")
    throw unsupported(OauthProviderCapability.REFRESH_TOKEN)
  }

  override fun revokeToken(request: OauthTokenRevokeRequest) {
    log.info("Apple OAuth token revoke is not supported in this provider implementation.")
    throw unsupported(OauthProviderCapability.REVOKE_TOKEN)
  }

  override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
    val strategy = resolveStrategy(request)
    log.debug("Resolving Apple OAuth identity with strategy={}.", strategy)
    return when (strategy) {
      OauthIdentityStrategy.ID_TOKEN -> resolveIdentityFromIdToken(request)
      OauthIdentityStrategy.USER_INFO_API ->
          throw unsupported(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO)
      OauthIdentityStrategy.AUTO ->
          throw InvalidOauthRequestException(
              "AUTO identity strategy could not be resolved for APPLE.")
    }
  }

  private fun resolveStrategy(request: OauthIdentityRequest): OauthIdentityStrategy {
    return when (request.strategy) {
      OauthIdentityStrategy.ID_TOKEN -> OauthIdentityStrategy.ID_TOKEN
      OauthIdentityStrategy.USER_INFO_API -> OauthIdentityStrategy.USER_INFO_API
      OauthIdentityStrategy.AUTO -> {
        if (request.idToken != null) {
          log.trace("AUTO strategy resolved to ID_TOKEN for APPLE.")
          OauthIdentityStrategy.ID_TOKEN
        } else {
          throw InvalidOauthRequestException("AUTO identity strategy requires idToken for APPLE.")
        }
      }
    }
  }

  private fun resolveIdentityFromIdToken(request: OauthIdentityRequest): OauthIdentityResult {
    requireCapability(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN)
    val idToken =
        request.idToken
            ?: throw InvalidOauthRequestException("idToken is required for ID_TOKEN strategy.")
    validateNonceRequired(request.nonce)

    log.debug("Verifying Apple id token.")
    val requiredAudience = audValidator?.invoke(request.audience) ?: request.audience
    val verifiedClaims =
        idTokenParser.verifyIdTokenClaims(
            jwt = idToken,
            requiredAudience = requiredAudience,
            expectedNonce = request.nonce,
        )
    val claimsMap = verifiedClaims.claims

    val userId =
        verifiedClaims.subject?.takeIf { it.isNotBlank() }
            ?: throw InvalidOauthRequestException("Apple id token does not include subject.")
    log.debug("Resolved Apple OAuth identity for userId={}.", userId)

    val email = getEmail(claimsMap)
    val emailVerified = getEmailVerified(claimsMap)
    val displayName = verifiedClaims.stringClaim("name")
    val pictureUrl = verifiedClaims.stringClaim("picture")

    return buildIdentityResult(
        request = request,
        userId = userId,
        email = email,
        emailVerified = emailVerified,
        displayName = displayName,
        pictureUrl = pictureUrl,
        fullClaims = claimsMap,
    )
  }

  private fun buildIdentityResult(
      request: OauthIdentityRequest,
      userId: String,
      email: String?,
      emailVerified: Boolean? = null,
      displayName: String?,
      pictureUrl: String?,
      fullClaims: Map<String, Any?>,
  ): OauthIdentityResult {
    val payloadMode = request.payloadMode
    val scopes = resolveScopes(request.scopes, request.scopePreset)
    val idOnlyClaims = mapOf("sub" to userId)
    val normalizedProfileMetadata =
        linkedMapOf<String, Any?>().apply {
          fullClaims["given_name"]?.let { put("given_name", it) }
          fullClaims["family_name"]?.let { put("family_name", it) }
          fullClaims["locale"]?.let { put("locale", it) }
        }
    val basicClaims =
        linkedMapOf<String, Any?>(
            "sub" to userId,
            "email" to email,
            "email_verified" to emailVerified,
            "name" to displayName,
            "picture" to pictureUrl,
        )

    return when (payloadMode) {
      OauthIdentityPayloadMode.ID_ONLY ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              providerSubject = userId,
              scopes = scopes,
              payloadMode = payloadMode,
              claims = idOnlyClaims,
              rawProfile = idOnlyClaims,
          )
      OauthIdentityPayloadMode.BASIC_PROFILE ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              providerSubject = userId,
              email = email,
              emailVerified = emailVerified,
              displayName = displayName,
              pictureUrl = pictureUrl,
              scopes = scopes,
              payloadMode = payloadMode,
              normalizedProfileMetadata = normalizedProfileMetadata,
              claims = basicClaims,
              rawProfile = basicClaims,
          )
      OauthIdentityPayloadMode.FULL_PROFILE ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              providerSubject = userId,
              email = email,
              emailVerified = emailVerified,
              displayName = displayName,
              pictureUrl = pictureUrl,
              scopes = scopes,
              payloadMode = payloadMode,
              normalizedProfileMetadata = normalizedProfileMetadata,
              claims = fullClaims,
              rawProfile = fullClaims,
          )
    }
  }

  private fun validateNonceRequired(expectedNonce: String?) {
    if (requireNonceValidation && expectedNonce.isNullOrBlank()) {
      throw InvalidOauthRequestException("nonce is required for Apple id token validation.")
    }
  }

  private fun resolveScopes(
      requestScopes: Set<String>,
      scopePreset: OauthScopePreset?
  ): Set<String> {
    if (requestScopes.isNotEmpty()) {
      return requestScopes
    }
    if (scopePreset != null) {
      return scopesForPreset(scopePreset)
    }
    return defaultScopes
  }

  private fun scopesForPreset(scopePreset: OauthScopePreset): Set<String> {
    return when (scopePreset) {
      OauthScopePreset.ID_ONLY -> emptySet()
      OauthScopePreset.BASIC_PROFILE -> setOf("name")
      OauthScopePreset.FULL_PROFILE -> setOf("name", "email")
    }
  }

  private fun getEmail(claims: Map<String, Any?>): String? {
    return try {
      (claims["email"] as? String)?.takeIf {
        it.isNotBlank() && !it.equals("null", ignoreCase = true)
      }
    } catch (e: Exception) {
      log.warn("Failed to read email from Apple id token claims.", e)
      null
    }
  }

  private fun getEmailVerified(claims: Map<String, Any?>): Boolean? {
    return when (val value = claims["email_verified"]) {
      is Boolean -> value
      is String -> value.equals("true", ignoreCase = true)
      else -> null
    }
  }

  private fun validatePkceUnsupported(request: OauthAuthorizationRequest) {
    if (request.codeChallenge == null && request.codeChallengeMethod == null) {
      return
    }
    val capability =
        when (request.codeChallengeMethod ?: OauthCodeChallengeMethod.S256) {
          OauthCodeChallengeMethod.S256 -> OauthProviderCapability.AUTHORIZATION_PKCE_S256
          OauthCodeChallengeMethod.PLAIN -> OauthProviderCapability.AUTHORIZATION_PKCE_PLAIN
        }
    throw unsupported(capability)
  }

  private fun unsupported(capability: OauthProviderCapability): UnsupportedOauthOperationException {
    return UnsupportedOauthOperationException.fromCapability(
        provider = providerName,
        capability = capability,
    )
  }

  private fun requireCapability(capability: OauthProviderCapability) {
    if (!supports(capability)) {
      log.trace("Apple OAuth capability check failed for capability={}.", capability)
      throw unsupported(capability)
    }
  }
}
