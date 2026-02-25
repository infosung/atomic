package com.infosung.atomic.oauth.provider.google

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
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
import com.infosung.atomic.oauth.exception.HttpIOException
import com.infosung.atomic.oauth.exception.HttpJwtVerifyException
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.exception.UnsupportedOauthOperationException
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.support.encodeQuery
import com.infosung.atomic.oauth.support.parseScopes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

class GoogleOauthProvider(
    private val clientId: String,
    private val clientSecret: String,
    private val serverRedirectUri: String,
    private val authorizationGrantType: String = "authorization_code",
    private val client: RestClient,
    private val googleIdTokenVerifier: GoogleIdTokenVerifier,
    private val stateManager: OauthStateManager,
    private val defaultScopes: Set<String> = setOf("openid", "email", "profile"),
    private val supportedScopes: Set<String>? = null,
    private val userInfoEndpoint: String = "https://openidconnect.googleapis.com/v1/userinfo",
    allowedAudiences: Set<String> = setOf(clientId),
    private val requireNonceValidation: Boolean = false,
) : OauthProvider {
  private val log: Logger = LoggerFactory.getLogger(this::class.java)
  private val allowedAudienceSet: Set<String> =
      allowedAudiences.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

  override val providerName: OauthProviderName = OauthProviderName.GOOGLE

  private val capabilitySet =
      setOf(
          OauthProviderCapability.AUTHORIZATION_URL,
          OauthProviderCapability.EXCHANGE_TOKEN,
          OauthProviderCapability.REFRESH_TOKEN,
          OauthProviderCapability.REVOKE_TOKEN,
          OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN,
          OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO,
          OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY,
          OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE,
          OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE,
      )

  private val authorizationReservedKeys =
      setOf("client_id", "redirect_uri", "response_type", "scope", "access_type", "state")

  private val exchangeReservedKeys =
      setOf("client_id", "client_secret", "redirect_uri", "grant_type", "code")

  private val refreshReservedKeys =
      setOf("grant_type", "client_id", "client_secret", "refresh_token", "scope")

  private val revokeReservedKeys = setOf("token")

  override fun capabilities(): Set<OauthProviderCapability> = capabilitySet

  init {
    require(allowedAudienceSet.isNotEmpty()) {
      "allowedAudiences must not be empty for GOOGLE provider."
    }
  }

  override fun buildAuthorizationUrl(request: OauthAuthorizationRequest): String {
    requireCapability(OauthProviderCapability.AUTHORIZATION_URL)
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
    val params =
        linkedMapOf(
            "client_id" to clientId,
            // OAuth provider callback endpoint must stay as server redirect URI.
            "redirect_uri" to serverRedirectUri,
            "response_type" to "code",
            "access_type" to "offline",
            "state" to state,
        )
    if (scopes.isNotEmpty()) {
      params["scope"] = scopes.joinToString(" ")
    }
    request.prompt?.let { params["prompt"] = it }
    request.loginHint?.let { params["login_hint"] = it }
    request.nonce?.let { params["nonce"] = it }
    request.responseMode?.let { params["response_mode"] = it }
    putAdditionalQueryParams(
        params, request.additionalParameters, authorizationReservedKeys, "auth")

    log.debug("Building Google OAuth authorization URL with scopeCount={}.", scopes.size)
    return encodeQuery("https://accounts.google.com/o/oauth2/v2/auth", params)
  }

  override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
    requireCapability(OauthProviderCapability.EXCHANGE_TOKEN)
    log.debug("Exchanging Google OAuth authorization code for token.")
    // Token exchange must use the same server callback URI registered at provider.
    val providerRedirectUri = serverRedirectUri
    val state =
        request.state.takeIf { it.isNotBlank() }
            ?: throw InvalidOauthRequestException("state must not be blank for token exchange.")
    stateManager.verifyState(
        signedState = state,
        expectedProvider = providerName,
    )
    log.trace("Google OAuth state verification succeeded for token exchange.")

    val tokenBody = LinkedMultiValueMap<String, String>()
    tokenBody.add("client_id", clientId)
    tokenBody.add("client_secret", clientSecret)
    tokenBody.add("redirect_uri", providerRedirectUri)
    tokenBody.add("grant_type", authorizationGrantType)
    tokenBody.add("code", request.code)
    putAdditionalFormParams(
        tokenBody, request.additionalParameters, exchangeReservedKeys, "exchange")

    val response =
        client
            .post()
            .uri("https://oauth2.googleapis.com/token")
            .body(tokenBody)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .retrieve()
            .body(GoogleExchangeTokenResponse::class.java)
            ?: throw HttpIOException("Failed to fetch Google OAuth token response.")
    log.trace("Received Google OAuth exchange response with tokenType={}.", response.token_type)

    return response.toTokenResult()
  }

  override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
    requireCapability(OauthProviderCapability.REFRESH_TOKEN)
    log.debug("Refreshing Google OAuth token with refresh token.")

    val requestBody = LinkedMultiValueMap<String, String>()
    requestBody.add("grant_type", "refresh_token")
    requestBody.add("client_id", clientId)
    requestBody.add("client_secret", clientSecret)
    requestBody.add("refresh_token", request.refreshToken)

    val requestedScopes = resolveOptionalScopes(request.scopes, request.scopePreset)
    if (requestedScopes.isNotEmpty()) {
      requestBody.add("scope", requestedScopes.joinToString(" "))
    }
    putAdditionalFormParams(
        requestBody, request.additionalParameters, refreshReservedKeys, "refresh")

    val response =
        client
            .post()
            .uri("https://oauth2.googleapis.com/token")
            .body(requestBody)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .retrieve()
            .body(GoogleExchangeTokenResponse::class.java)
            ?: throw HttpIOException("Failed to refresh Google OAuth token.")
    log.trace("Received Google OAuth refresh response with tokenType={}.", response.token_type)

    return response.toTokenResult()
  }

  override fun revokeToken(request: OauthTokenRevokeRequest) {
    requireCapability(OauthProviderCapability.REVOKE_TOKEN)
    log.info("Revoking Google OAuth token.")

    val revokeBody = LinkedMultiValueMap<String, String>()
    revokeBody.add("token", request.accessToken)
    putAdditionalFormParams(revokeBody, request.additionalParameters, revokeReservedKeys, "revoke")

    client
        .post()
        .uri("https://oauth2.googleapis.com/revoke")
        .body(revokeBody)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .retrieve()
  }

  override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
    val strategy = resolveStrategy(request)
    log.debug("Resolving Google OAuth identity with strategy={}.", strategy)
    return when (strategy) {
      OauthIdentityStrategy.ID_TOKEN -> resolveIdentityFromIdToken(request)
      OauthIdentityStrategy.USER_INFO_API -> resolveIdentityFromUserInfo(request)
      OauthIdentityStrategy.AUTO ->
          throw InvalidOauthRequestException(
              "AUTO identity strategy could not be resolved for GOOGLE.")
    }
  }

  private fun resolveStrategy(request: OauthIdentityRequest): OauthIdentityStrategy {
    return when (request.strategy) {
      OauthIdentityStrategy.ID_TOKEN -> OauthIdentityStrategy.ID_TOKEN
      OauthIdentityStrategy.USER_INFO_API -> OauthIdentityStrategy.USER_INFO_API
      OauthIdentityStrategy.AUTO ->
          when {
            request.idToken != null &&
                supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN) -> {
              log.trace("AUTO strategy resolved to ID_TOKEN for GOOGLE.")
              OauthIdentityStrategy.ID_TOKEN
            }
            request.accessToken != null &&
                supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO) -> {
              log.trace("AUTO strategy resolved to USER_INFO_API for GOOGLE.")
              OauthIdentityStrategy.USER_INFO_API
            }
            else ->
                throw InvalidOauthRequestException(
                    "AUTO identity strategy requires idToken or accessToken for GOOGLE.",
                )
          }
    }
  }

  private fun resolveIdentityFromIdToken(request: OauthIdentityRequest): OauthIdentityResult {
    requireCapability(OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN)
    val idToken =
        request.idToken
            ?: throw InvalidOauthRequestException("idToken is required for ID_TOKEN strategy.")
    validateNonceRequired(request.nonce)

    log.debug("Verifying Google id token.")
    val token =
        googleIdTokenVerifier.verify(idToken)
            ?: throw HttpJwtVerifyException("Failed to verify Google id token.")

    val payload = token.payload
    val audiences = extractAudienceValues(payload["aud"] ?: payload.audience)
    if (audiences.none { allowedAudienceSet.contains(it) }) {
      throw HttpJwtVerifyException(
          "Google id token audience does not match configured allowed audiences.",
      )
    }
    if (!request.audience.isNullOrBlank() && !allowedAudienceSet.contains(request.audience)) {
      throw InvalidOauthRequestException(
          "Requested audience is not in configured allowed audiences.")
    }
    if (!request.audience.isNullOrBlank() && !audiences.contains(request.audience)) {
      throw HttpJwtVerifyException(
          "Google id token audience does not contain requested audience.",
      )
    }
    if (!request.nonce.isNullOrBlank() && request.nonce != payload["nonce"]?.toString()) {
      throw HttpJwtVerifyException("Google id token nonce does not match expected nonce.")
    }

    val claims =
        linkedMapOf<String, Any?>(
            "sub" to payload.subject,
            "email" to payload.email,
            "email_verified" to payload.emailVerified,
            "name" to payload["name"],
            "given_name" to payload["given_name"],
            "family_name" to payload["family_name"],
            "picture" to payload["picture"],
            "aud" to (payload["aud"] ?: payload.audience),
            "iss" to payload.issuer,
            "exp" to payload.expirationTimeSeconds,
            "iat" to payload.issuedAtTimeSeconds,
            "nonce" to payload["nonce"],
        )
    val userId =
        payload.subject ?: throw HttpJwtVerifyException("Google id token does not include subject.")
    log.debug("Resolved Google OAuth identity from id token for userId={}.", userId)

    val email = payload.email
    val displayName = payload["name"] as? String
    val pictureUrl = payload["picture"] as? String
    return buildIdentityResult(
        request = request,
        userId = userId,
        email = email,
        displayName = displayName,
        pictureUrl = pictureUrl,
        fullClaims = claims,
    )
  }

  private fun resolveIdentityFromUserInfo(request: OauthIdentityRequest): OauthIdentityResult {
    requireCapability(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO)
    val accessToken =
        request.accessToken
            ?: throw InvalidOauthRequestException(
                "accessToken is required for USER_INFO_API strategy.",
            )

    log.debug("Requesting Google user info endpoint.")
    val queryParams =
        LinkedHashMap<String, String>().apply {
          putAll(request.userInfoParameters)
          request.additionalParameters.forEach { (key, value) -> put(key, value) }
        }
    val endpoint = request.userInfoEndpoint ?: userInfoEndpoint
    val url = if (queryParams.isEmpty()) endpoint else encodeQuery(endpoint, queryParams)

    val response =
        client
            .get()
            .uri(url)
            .headers { header -> header.add("Authorization", "Bearer $accessToken") }
            .retrieve()
            .toEntity(GoogleUserInfoResponse::class.java)
            .body ?: throw HttpIOException("Failed to fetch Google user info.")

    val userId =
        response.sub
            ?: response.id
            ?: throw HttpIOException(
                "Google user info response does not include subject identifier.")
    log.debug("Resolved Google OAuth identity from user info for userId={}.", userId)

    val emailVerified = response.email_verified ?: response.verified_email
    val rawProfile =
        linkedMapOf<String, Any?>(
            "sub" to response.sub,
            "id" to response.id,
            "email" to response.email,
            "email_verified" to emailVerified,
            "name" to response.name,
            "given_name" to response.given_name,
            "family_name" to response.family_name,
            "picture" to response.picture,
            "hd" to response.hd,
        )

    return buildIdentityResult(
        request = request,
        userId = userId,
        email = response.email,
        displayName = response.name,
        pictureUrl = response.picture,
        fullClaims = rawProfile,
    )
  }

  private fun buildIdentityResult(
      request: OauthIdentityRequest,
      userId: String,
      email: String?,
      displayName: String?,
      pictureUrl: String?,
      fullClaims: Map<String, Any?>,
  ): OauthIdentityResult {
    val payloadMode = request.payloadMode
    val scopes = resolveScopes(request.scopes, request.scopePreset)
    val idOnlyClaims = mapOf("sub" to userId)
    val basicClaims =
        linkedMapOf<String, Any?>(
            "sub" to userId,
            "email" to email,
            "name" to displayName,
            "picture" to pictureUrl,
        )

    return when (payloadMode) {
      OauthIdentityPayloadMode.ID_ONLY ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              scopes = scopes,
              payloadMode = payloadMode,
              claims = idOnlyClaims,
              rawProfile = idOnlyClaims,
          )
      OauthIdentityPayloadMode.BASIC_PROFILE ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              email = email,
              displayName = displayName,
              pictureUrl = pictureUrl,
              scopes = scopes,
              payloadMode = payloadMode,
              claims = basicClaims,
              rawProfile = basicClaims,
          )
      OauthIdentityPayloadMode.FULL_PROFILE ->
          OauthIdentityResult(
              provider = providerName,
              userId = userId,
              email = email,
              displayName = displayName,
              pictureUrl = pictureUrl,
              scopes = scopes,
              payloadMode = payloadMode,
              claims = fullClaims,
              rawProfile = fullClaims,
          )
    }
  }

  private fun resolveScopes(
      requestScopes: Set<String>,
      scopePreset: OauthScopePreset?
  ): Set<String> {
    val resolved =
        when {
          requestScopes.isNotEmpty() -> requestScopes
          scopePreset != null -> scopesForPreset(scopePreset)
          else -> defaultScopes
        }

    log.trace(
        "Resolving Google scopes. requestScopeCount={}, resolvedScopeCount={}.",
        requestScopes.size,
        resolved.size,
    )

    if (resolved.isEmpty()) {
      return emptySet()
    }
    if (supportedScopes != null && !supportedScopes.containsAll(resolved)) {
      val unsupported = resolved.filter { !supportedScopes.contains(it) }
      throw InvalidOauthRequestException(
          "Unsupported Google scopes requested: ${unsupported.joinToString(",")}",
      )
    }
    return resolved
  }

  private fun resolveOptionalScopes(
      requestScopes: Set<String>,
      scopePreset: OauthScopePreset?,
  ): Set<String> {
    if (requestScopes.isEmpty() && scopePreset == null) {
      return emptySet()
    }
    return resolveScopes(requestScopes, scopePreset)
  }

  private fun scopesForPreset(scopePreset: OauthScopePreset): Set<String> {
    return when (scopePreset) {
      OauthScopePreset.ID_ONLY -> setOf("openid")
      OauthScopePreset.BASIC_PROFILE -> setOf("openid", "email")
      OauthScopePreset.FULL_PROFILE -> setOf("openid", "email", "profile")
    }
  }

  private fun extractAudienceValues(rawAudience: Any?): Set<String> {
    return when (rawAudience) {
      null -> emptySet()
      is String -> setOf(rawAudience)
      is Collection<*> -> rawAudience.mapNotNull { it?.toString() }.toSet()
      else -> setOf(rawAudience.toString())
    }
  }

  private fun validateNonceRequired(expectedNonce: String?) {
    if (requireNonceValidation && expectedNonce.isNullOrBlank()) {
      throw InvalidOauthRequestException("nonce is required for Google id token validation.")
    }
  }

  private fun putAdditionalQueryParams(
      target: MutableMap<String, String>,
      additionalParameters: Map<String, String>,
      reservedKeys: Set<String>,
      operation: String,
  ) {
    additionalParameters.forEach { (key, value) ->
      if (reservedKeys.contains(key)) {
        log.warn("Ignoring Google {} additional parameter override for key={}.", operation, key)
      } else {
        target[key] = value
      }
    }
  }

  private fun putAdditionalFormParams(
      target: LinkedMultiValueMap<String, String>,
      additionalParameters: Map<String, String>,
      reservedKeys: Set<String>,
      operation: String,
  ) {
    additionalParameters.forEach { (key, value) ->
      if (reservedKeys.contains(key)) {
        log.warn("Ignoring Google {} additional parameter override for key={}.", operation, key)
      } else {
        target.add(key, value)
      }
    }
  }

  private fun requireCapability(capability: OauthProviderCapability) {
    if (!supports(capability)) {
      log.trace("Google OAuth capability check failed for capability={}.", capability)
      throw UnsupportedOauthOperationException.fromCapability(providerName, capability)
    }
  }

  private fun GoogleExchangeTokenResponse.toTokenResult(): OauthTokenResult {
    return OauthTokenResult(
        accessToken = access_token,
        refreshToken = refresh_token,
        idToken = id_token,
        tokenType = token_type,
        expiresInSeconds = expires_in,
        scopes = parseScopes(scope),
        raw =
            mapOf(
                "access_token" to access_token,
                "refresh_token" to refresh_token,
                "id_token" to id_token,
                "token_type" to token_type,
                "expires_in" to expires_in,
                "scope" to scope,
            ),
    )
  }
}
