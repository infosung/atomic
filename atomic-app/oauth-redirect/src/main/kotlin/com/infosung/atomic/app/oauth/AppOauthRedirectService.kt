package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.api.OauthTokenResult
import com.infosung.atomic.oauth.exception.OauthException
import com.infosung.atomic.oauth.state.OauthStateManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt

/** Redirect/callback orchestration for OAuth relayCode flow. */
class AppOauthRedirectService(
    private val oauthServiceProvider: OauthServiceProvider,
    private val oauthStateManager: OauthStateManager,
    private val relayCodeService: AppOauthRelayCodeService,
    private val properties: AtomicAppOauthRedirectProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Builds provider authorization redirect URL.
   *
   * @throws HttpStatusException 400 when provider or redirectUri is invalid.
   * @throws IllegalStateException when required redirect configuration is missing.
   */
  fun buildAuthorizationRedirectUrl(
      provider: String,
      redirectUri: String,
      nonce: String?,
      prompt: String?,
      loginHint: String?,
      responseMode: String?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): String {
    val oauthProvider = resolveProvider(provider)
    val normalizedRedirectUri = validateRedirectUri(redirectUri)
    val authorizationUrl =
        oauthProvider.buildAuthorizationUrl(
            OauthAuthorizationRequest(
                redirectUri = normalizedRedirectUri,
                nonce = nonce?.takeIf { it.isNotBlank() },
                prompt = prompt?.takeIf { it.isNotBlank() },
                loginHint = loginHint?.takeIf { it.isNotBlank() },
                responseMode = responseMode?.takeIf { it.isNotBlank() },
                stateAttributes = buildStateAttributes(callbackBindingToken),
                additionalParameters = additionalParameters,
            ),
        )
    log.debug(
        "Built oauth authorization URL: provider={}, redirectUri={}",
        oauthProvider.providerName,
        normalizedRedirectUri,
    )
    return authorizationUrl
  }

  /**
   * Handles OAuth callback for providers supporting code exchange (for example Google/Kakao).
   *
   * @throws HttpStatusException 400 when provider/state/request is invalid.
   */
  fun buildCallbackRedirectUrl(
      provider: String,
      code: String,
      state: String,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): String {
    return mapCallbackErrors(provider = provider) {
      val oauthProvider = resolveProvider(provider)
      if (oauthProvider.providerName == OauthProviderName.APPLE) {
        throw HttpStatusException(
            status = 400,
            message = "Use POST ${properties.callbackEndpointPath}/apple for Apple callback.",
        )
      }
      log.debug(
          "Verifying oauth callback state: provider={}, additionalParameterKeys={}",
          oauthProvider.providerName,
          additionalParameters.keys.sorted(),
      )

      val stateJwt =
          oauthStateManager.verifyState(
              signedState = state,
              expectedProvider = oauthProvider.providerName,
          )
      validateCallbackBinding(stateJwt, callbackBindingToken)
      val tokenResult =
          oauthProvider.exchangeCode(
              OauthTokenExchangeRequest(
                  code = code,
                  state = state,
                  additionalParameters = additionalParameters,
              ),
          )

      val relayCode =
          relayCodeService.issueRelayCode(
              toRelayPayload(
                  provider = oauthProvider.providerName,
                  tokenResult = tokenResult,
                  stateJwt = stateJwt,
              ),
          )
      val redirectUri = readRedirectUri(stateJwt)
      val redirectWithRelay =
          appendQueryParameter(
              url = redirectUri,
              key = resolveRelayCodeQueryParameterName(),
              value = relayCode,
          )
      log.debug(
          "OAuth callback processed: provider={}, redirectUri={}, relayCodeLength={}",
          oauthProvider.providerName,
          redirectUri,
          relayCode.length,
      )
      redirectWithRelay
    }
  }

  /**
   * Handles Apple callback from form POST and returns frontend redirect URL with relayCode.
   *
   * @throws HttpStatusException 400 when state or request is invalid.
   */
  fun buildAppleCallbackRedirectUrl(
      state: String,
      idToken: String,
      code: String?,
      user: String?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String? = null,
  ): String {
    return mapCallbackErrors(provider = OauthProviderName.APPLE.name) {
      val appleProvider = resolveProvider(OauthProviderName.APPLE.name)
      val stateJwt =
          oauthStateManager.verifyState(
              signedState = state,
              expectedProvider = appleProvider.providerName,
          )
      validateCallbackBinding(stateJwt, callbackBindingToken)

      val raw = linkedMapOf<String, Any?>()
      code?.takeIf { it.isNotBlank() }?.let { raw["code"] = it }
      user?.takeIf { it.isNotBlank() }?.let { raw["user"] = it }
      raw.putAll(additionalParameters)

      val tokenResult =
          OauthTokenResult(
              idToken = idToken,
              raw = raw,
          )
      val relayCode =
          relayCodeService.issueRelayCode(
              toRelayPayload(
                  provider = OauthProviderName.APPLE,
                  tokenResult = tokenResult,
                  stateJwt = stateJwt,
              ),
          )
      val redirectUri = readRedirectUri(stateJwt)
      val redirectWithRelay =
          appendQueryParameter(
              url = redirectUri,
              key = resolveRelayCodeQueryParameterName(),
              value = relayCode,
          )
      log.debug(
          "Apple oauth callback processed: redirectUri={}, relayCodeLength={}",
          redirectUri,
          relayCode.length,
      )
      redirectWithRelay
    }
  }

  private fun resolveProvider(provider: String) =
      oauthServiceProvider.getService(provider)
          ?: throw HttpStatusException(status = 400, message = "Unsupported provider: $provider")

  private inline fun <T> mapCallbackErrors(
      provider: String,
      block: () -> T,
  ): T {
    return try {
      block()
    } catch (e: HttpStatusException) {
      log.warn("Rejected oauth callback: provider={}, message={}", provider, e.message)
      throw e
    } catch (e: OauthException) {
      log.warn("Rejected oauth callback: provider={}, message={}", provider, e.message)
      throw HttpStatusException(
          status = 400,
          message = e.message ?: "Invalid OAuth callback request for provider: $provider",
          cause = e,
      )
    } catch (e: IllegalArgumentException) {
      log.warn("Rejected oauth callback: provider={}, message={}", provider, e.message)
      throw HttpStatusException(
          status = 400,
          message = e.message ?: "Invalid OAuth callback request for provider: $provider",
          cause = e,
      )
    }
  }

  private fun validateRedirectUri(redirectUri: String): String {
    return AllowedRedirectUriPolicy.validateRedirectUri(
        redirectUri = redirectUri,
        configuredPrefixes = properties.allowedRedirectUriPrefixes,
    )
  }

  private fun readRedirectUri(stateJwt: Jwt): String {
    val redirectUri =
        stateJwt.claims["redirect_uri"]?.toString()
            ?: throw HttpStatusException(
                status = 400, message = "State does not include redirect_uri.")
    return validateRedirectUri(redirectUri)
  }

  private fun resolveRelayCodeQueryParameterName(): String {
    val key = properties.relayCodeQueryParameterName.trim()
    if (key.isBlank()) {
      throw IllegalStateException(
          "atomic.app.oauth.redirect.relay-code-query-parameter-name must not be blank.",
      )
    }
    return key
  }

  private fun buildStateAttributes(callbackBindingToken: String?): Map<String, String> {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return emptyMap()
    }
    val stateAttributeKey = resolveCallbackBindingStateAttributeKey()
    val bindingToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw HttpStatusException(
                status = 400,
                message = "OAuth callback binding token is required.",
            )
    log.trace(
        "Using callback binding token for oauth redirect request: callbackBindingMode={}",
        properties.callbackBinding.resolvedMode(),
    )
    return mapOf(stateAttributeKey to bindingToken)
  }

  private fun validateCallbackBinding(
      stateJwt: Jwt,
      callbackBindingToken: String?,
  ) {
    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return
    }
    val stateAttributeKey = resolveCallbackBindingStateAttributeKey()
    val stateAttributes = readStateAttributes(stateJwt)
    val expectedToken =
        stateAttributes[stateAttributeKey]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw HttpStatusException(
                status = 400,
                message = "OAuth callback binding state is missing.",
            )
    val actualToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw HttpStatusException(
                status = 400,
                message = "OAuth callback binding cookie is missing.",
            )
    if (actualToken != expectedToken) {
      log.warn(
          "OAuth callback binding token mismatch detected: callbackBindingMode={}",
          properties.callbackBinding.resolvedMode(),
      )
      throw HttpStatusException(
          status = 400,
          message = "OAuth callback binding token mismatch.",
      )
    }
  }

  private fun resolveCallbackBindingStateAttributeKey(): String {
    val key = properties.callbackBinding.stateAttributeKey.trim()
    if (key.isBlank()) {
      throw IllegalStateException(
          "atomic.app.oauth.redirect.callback-binding.state-attribute-key must not be blank when callback binding is enabled.",
      )
    }
    return key
  }

  private fun appendQueryParameter(
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

  @Suppress("UNCHECKED_CAST")
  private fun readStateAttributes(stateJwt: Jwt): Map<String, String> {
    val raw = stateJwt.claims["attributes"] ?: return emptyMap()
    val map = raw as? Map<*, *> ?: return emptyMap()
    return map.entries.associate { (key, value) -> key.toString() to value.toString() }
  }

  private fun toRelayPayload(
      provider: OauthProviderName,
      tokenResult: OauthTokenResult,
      stateJwt: Jwt,
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
        nonce = stateJwt.claims["nonce"]?.toString(),
        stateAttributes = readStateAttributes(stateJwt),
    )
  }
}
