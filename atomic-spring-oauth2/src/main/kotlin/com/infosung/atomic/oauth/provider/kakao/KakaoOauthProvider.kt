package com.infosung.atomic.oauth.provider.kakao

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
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.exception.UnsupportedOauthOperationException
import com.infosung.atomic.oauth.idtoken.IdTokenParser
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.support.encodeQuery
import com.infosung.atomic.oauth.support.parseScopes
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * Kakao OAuth provider implementation.
 *
 * Supports authorization URL, code exchange, refresh, and identity resolution.
 */
class KakaoOauthProvider(
    private val client: RestClient,
    private val clientId: String,
    private val clientSecret: String?,
    private val serverRedirectUri: String,
    private val idTokenParser: IdTokenParser,
    private val stateManager: OauthStateManager,
    private val audValidator: ((String?) -> String)? = null,
    private val defaultScopes: Set<String> = setOf("openid"),
    private val supportedScopes: Set<String>? = null,
    private val userInfoEndpoint: String = "https://kapi.kakao.com/v1/oidc/userinfo",
    private val requireNonceValidation: Boolean = true,
) : OauthProvider {
  private val log = LoggerFactory.getLogger(this::class.java)

  override val providerName: OauthProviderName = OauthProviderName.KAKAO

  private val capabilitySet =
      setOf(
          OauthProviderCapability.AUTHORIZATION_URL,
          OauthProviderCapability.EXCHANGE_TOKEN,
          OauthProviderCapability.REFRESH_TOKEN,
          OauthProviderCapability.RESOLVE_IDENTITY_WITH_ID_TOKEN,
          OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO,
          OauthProviderCapability.RESOLVE_IDENTITY_ID_ONLY,
          OauthProviderCapability.RESOLVE_IDENTITY_BASIC_PROFILE,
          OauthProviderCapability.RESOLVE_IDENTITY_FULL_PROFILE,
      )

  private val authorizationReservedKeys =
      setOf("client_id", "redirect_uri", "response_type", "scope", "state")

  private val exchangeReservedKeys =
      setOf("grant_type", "client_id", "redirect_uri", "code", "state", "client_secret", "scope")

  private val refreshReservedKeys =
      setOf("grant_type", "client_id", "refresh_token", "client_secret", "scope")

  override fun capabilities(): Set<OauthProviderCapability> = capabilitySet

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
    log.debug("Building Kakao OAuth authorization URL with scopeCount={}.", scopes.size)

    val params =
        linkedMapOf(
            "client_id" to clientId,
            // OAuth provider callback endpoint must stay as server redirect URI.
            "redirect_uri" to serverRedirectUri,
            "response_type" to "code",
            "state" to state,
        )
    if (scopes.isNotEmpty()) {
      params["scope"] = scopes.joinToString(",")
    }
    request.prompt?.let { params["prompt"] = it }
    request.loginHint?.let { params["login_hint"] = it }
    request.nonce?.let { params["nonce"] = it }
    request.responseMode?.let { params["response_mode"] = it }
    putAdditionalQueryParams(
        params, request.additionalParameters, authorizationReservedKeys, "auth")

    return encodeQuery("https://kauth.kakao.com/oauth/authorize", params)
  }

  override fun exchangeCode(request: OauthTokenExchangeRequest): OauthTokenResult {
    requireCapability(OauthProviderCapability.EXCHANGE_TOKEN)
    log.debug("Exchanging Kakao OAuth authorization code for token.")
    // Token exchange must use the same server callback URI registered at provider.
    val providerRedirectUri = serverRedirectUri
    val state =
        request.state.takeIf { it.isNotBlank() }
            ?: throw InvalidOauthRequestException("state must not be blank for token exchange.")
    stateManager.verifyState(
        signedState = state,
        expectedProvider = providerName,
    )
    log.trace("Kakao OAuth state verification succeeded for token exchange.")

    val requestBody = LinkedMultiValueMap<String, String>()
    requestBody.add("grant_type", "authorization_code")
    requestBody.add("client_id", clientId)
    requestBody.add("redirect_uri", providerRedirectUri)
    requestBody.add("code", request.code)
    requestBody.add("state", state)
    clientSecret?.let { requestBody.add("client_secret", it) }

    val requestedScopes = resolveOptionalScopes(request.scopes, request.scopePreset)
    if (requestedScopes.isNotEmpty()) {
      requestBody.add("scope", requestedScopes.joinToString(","))
    }
    putAdditionalFormParams(
        requestBody, request.additionalParameters, exchangeReservedKeys, "exchange")

    val response =
        client
            .post()
            .uri("https://kauth.kakao.com/oauth/token")
            .body(requestBody)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .retrieve()
            .body(KakaoExchangeTokenResponse::class.java)
            ?: throw HttpIOException("Failed to exchange Kakao OAuth token.")
    log.trace("Received Kakao OAuth exchange response with tokenType={}.", response.token_type)

    return response.toTokenResult()
  }

  override fun refreshToken(request: OauthTokenRefreshRequest): OauthTokenResult {
    requireCapability(OauthProviderCapability.REFRESH_TOKEN)
    log.debug("Refreshing Kakao OAuth token with refresh token.")

    val requestBody = LinkedMultiValueMap<String, String>()
    requestBody.add("grant_type", "refresh_token")
    requestBody.add("client_id", clientId)
    requestBody.add("refresh_token", request.refreshToken)
    clientSecret?.let { requestBody.add("client_secret", it) }

    val requestedScopes = resolveOptionalScopes(request.scopes, request.scopePreset)
    if (requestedScopes.isNotEmpty()) {
      requestBody.add("scope", requestedScopes.joinToString(","))
    }
    putAdditionalFormParams(
        requestBody, request.additionalParameters, refreshReservedKeys, "refresh")

    val response =
        client
            .post()
            .uri("https://kauth.kakao.com/oauth/token")
            .body(requestBody)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .retrieve()
            .body(KakaoExchangeTokenResponse::class.java)
            ?: throw HttpIOException("Failed to refresh Kakao OAuth token.")
    log.trace("Received Kakao OAuth refresh response with tokenType={}.", response.token_type)

    return response.toTokenResult()
  }

  override fun revokeToken(request: OauthTokenRevokeRequest) {
    log.info("Kakao OAuth token revoke is not supported in this provider implementation.")
    throw UnsupportedOauthOperationException.fromCapability(
        provider = providerName,
        capability = OauthProviderCapability.REVOKE_TOKEN,
    )
  }

  override fun resolveIdentity(request: OauthIdentityRequest): OauthIdentityResult {
    val strategy = resolveStrategy(request)
    log.debug("Resolving Kakao OAuth identity with strategy={}.", strategy)
    return when (strategy) {
      OauthIdentityStrategy.ID_TOKEN -> resolveIdentityFromIdToken(request)
      OauthIdentityStrategy.USER_INFO_API -> resolveIdentityFromUserInfo(request)
      OauthIdentityStrategy.AUTO ->
          throw InvalidOauthRequestException(
              "AUTO identity strategy could not be resolved for KAKAO.")
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
              log.trace("AUTO strategy resolved to ID_TOKEN for KAKAO.")
              OauthIdentityStrategy.ID_TOKEN
            }
            request.accessToken != null &&
                supports(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO) -> {
              log.trace("AUTO strategy resolved to USER_INFO_API for KAKAO.")
              OauthIdentityStrategy.USER_INFO_API
            }
            else ->
                throw InvalidOauthRequestException(
                    "AUTO identity strategy requires idToken or accessToken for KAKAO.",
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

    log.debug("Verifying Kakao id token.")
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
            ?: throw InvalidOauthRequestException("Kakao id token does not include subject.")
    log.debug("Resolved Kakao OAuth identity from id token for userId={}.", userId)

    val email = getEmailFromClaims(claimsMap)
    val displayName = verifiedClaims.stringClaim("nickname")
    val pictureUrl = verifiedClaims.stringClaim("picture")
    return buildIdentityResult(
        request = request,
        userId = userId,
        email = email,
        displayName = displayName,
        pictureUrl = pictureUrl,
        fullClaims = claimsMap,
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun resolveIdentityFromUserInfo(request: OauthIdentityRequest): OauthIdentityResult {
    requireCapability(OauthProviderCapability.RESOLVE_IDENTITY_WITH_USER_INFO)
    val accessToken =
        request.accessToken
            ?: throw InvalidOauthRequestException(
                "accessToken is required for USER_INFO_API strategy.",
            )

    log.debug("Requesting Kakao user info endpoint.")
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
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java)
            ?.entries
            ?.associate { (key, value) -> key.toString() to value }
            ?: throw HttpIOException("Failed to fetch Kakao user info.")

    val userId =
        response["sub"]?.toString()
            ?: response["id"]?.toString()
            ?: throw HttpIOException("Kakao user info response does not include id.")

    log.debug("Resolved Kakao OAuth identity from user info for userId={}.", userId)

    val kakaoAccount = response["kakao_account"] as? Map<String, Any?>
    val properties = response["properties"] as? Map<String, Any?>
    val email = (response["email"] as? String) ?: kakaoAccount?.get("email") as? String
    val displayName = (response["nickname"] as? String) ?: (properties?.get("nickname") as? String)
    val pictureUrl =
        (response["picture"] as? String)
            ?: (response["profile_image"] as? String)
            ?: (properties?.get("profile_image") as? String)

    return buildIdentityResult(
        request = request,
        userId = userId,
        email = email,
        displayName = displayName,
        pictureUrl = pictureUrl,
        fullClaims = response,
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
            "nickname" to displayName,
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

  private fun validateNonceRequired(expectedNonce: String?) {
    if (requireNonceValidation && expectedNonce.isNullOrBlank()) {
      throw InvalidOauthRequestException("nonce is required for Kakao id token validation.")
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
        "Resolving Kakao scopes. requestScopeCount={}, resolvedScopeCount={}.",
        requestScopes.size,
        resolved.size,
    )

    if (resolved.isEmpty()) {
      return emptySet()
    }
    if (supportedScopes != null && !supportedScopes.containsAll(resolved)) {
      val unsupported = resolved.filter { !supportedScopes.contains(it) }
      throw InvalidOauthRequestException(
          "Unsupported Kakao scopes requested: ${unsupported.joinToString(",")}",
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
      OauthScopePreset.BASIC_PROFILE -> setOf("openid", "profile_nickname")
      OauthScopePreset.FULL_PROFILE ->
          setOf("openid", "profile_nickname", "profile_image", "account_email")
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
        log.warn("Ignoring Kakao {} additional parameter override for key={}.", operation, key)
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
        log.warn("Ignoring Kakao {} additional parameter override for key={}.", operation, key)
      } else {
        target.add(key, value)
      }
    }
  }

  private fun getEmailFromClaims(claims: Map<String, Any?>): String? {
    return try {
      (claims["email"] as? String)?.takeIf {
        it.isNotBlank() && !it.equals("null", ignoreCase = true)
      }
    } catch (e: Exception) {
      log.warn("Failed to read email from Kakao id token claims.", e)
      null
    }
  }

  private fun requireCapability(capability: OauthProviderCapability) {
    if (!supports(capability)) {
      log.trace("Kakao OAuth capability check failed for capability={}.", capability)
      throw UnsupportedOauthOperationException.fromCapability(providerName, capability)
    }
  }

  private fun KakaoExchangeTokenResponse.toTokenResult(): OauthTokenResult {
    return OauthTokenResult(
        accessToken = access_token,
        refreshToken = refresh_token,
        idToken = id_token,
        tokenType = token_type,
        expiresInSeconds = expires_in,
        scopes = parseScopes(scope?.replace(",", " ")),
        raw =
            mapOf(
                "token_type" to token_type,
                "access_token" to access_token,
                "id_token" to id_token,
                "expires_in" to expires_in,
                "refresh_token" to refresh_token,
                "refresh_token_expires_in" to refresh_token_expires_in,
                "scope" to scope,
            ),
    )
  }
}
