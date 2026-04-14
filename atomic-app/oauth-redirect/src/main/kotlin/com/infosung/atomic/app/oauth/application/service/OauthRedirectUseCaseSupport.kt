package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.model.OauthVerifiedState
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import com.infosung.atomic.oauth.api.OauthIdentityResult
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenResult
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

internal object OauthRedirectUseCaseSupport {
  internal const val INTERNAL_PKCE_REQUIRED_ATTRIBUTE_KEY = "__atomicPkceRequired"
  private val PKCE_CODE_VERIFIER_PATTERN = Regex("^[A-Za-z0-9._~\\-]{43,128}$")

  fun buildStateAttributes(
      callbackBindingEnabled: Boolean,
      callbackBindingStateAttributeKey: String,
      callbackBindingToken: String?,
      resolvedPkce: ResolvedPkce?,
  ): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    attributes.putAll(
        buildCallbackBindingStateAttributes(
            callbackBindingEnabled = callbackBindingEnabled,
            callbackBindingStateAttributeKey = callbackBindingStateAttributeKey,
            callbackBindingToken = callbackBindingToken,
        ),
    )
    resolvedPkce?.let { attributes[INTERNAL_PKCE_REQUIRED_ATTRIBUTE_KEY] = "true" }
    return attributes
  }

  fun buildCallbackBindingStateAttributes(
      callbackBindingEnabled: Boolean,
      callbackBindingStateAttributeKey: String,
      callbackBindingToken: String?,
  ): Map<String, String> {
    if (!callbackBindingEnabled) {
      return emptyMap()
    }
    val bindingToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding token is required.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    return mapOf(callbackBindingStateAttributeKey to bindingToken)
  }

  fun resolvePkce(
      codeVerifier: String?,
      codeChallengeMethod: OauthCodeChallengeMethod?,
  ): ResolvedPkce? {
    val normalizedCodeVerifier = codeVerifier?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedCodeVerifier == null && codeChallengeMethod == null) {
      return null
    }
    if (normalizedCodeVerifier == null || codeChallengeMethod == null) {
      throw OauthRedirectRequestException(
          message = "PKCE codeVerifier and codeChallengeMethod must be provided together.",
          errorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
      )
    }
    if (!PKCE_CODE_VERIFIER_PATTERN.matches(normalizedCodeVerifier)) {
      throw OauthRedirectRequestException(
          message = "PKCE codeVerifier must be 43..128 RFC7636 characters.",
          errorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
      )
    }
    val codeChallenge =
        when (codeChallengeMethod) {
          OauthCodeChallengeMethod.S256 ->
              Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(
                      MessageDigest.getInstance("SHA-256")
                          .digest(normalizedCodeVerifier.toByteArray(StandardCharsets.US_ASCII)),
                  )
          OauthCodeChallengeMethod.PLAIN -> normalizedCodeVerifier
        }
    return ResolvedPkce(
        codeVerifier = normalizedCodeVerifier,
        codeChallenge = codeChallenge,
        codeChallengeMethod = codeChallengeMethod,
    )
  }

  fun parseCodeChallengeMethod(rawValue: String?): OauthCodeChallengeMethod? {
    val normalizedValue = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { OauthCodeChallengeMethod.valueOf(normalizedValue.uppercase(Locale.ROOT)) }
        .getOrElse {
          throw OauthRedirectRequestException(
              message = "Unsupported PKCE codeChallengeMethod: $normalizedValue",
              errorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
          )
        }
  }

  fun normalizePkceCodeVerifier(codeVerifier: String?): String? {
    val normalizedCodeVerifier = codeVerifier?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!PKCE_CODE_VERIFIER_PATTERN.matches(normalizedCodeVerifier)) {
      throw OauthRedirectRequestException(
          message = "PKCE codeVerifier must be 43..128 RFC7636 characters.",
          errorCode = OauthRedirectErrorCode.OAUTH_REDIRECT_INVALID_REQUEST,
      )
    }
    return normalizedCodeVerifier
  }

  fun generatePkceCodeVerifier(secureRandom: SecureRandom): String {
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  fun validateCallbackBinding(
      verifiedState: OauthVerifiedState,
      callbackBindingEnabled: Boolean,
      callbackBindingStateAttributeKey: String,
      callbackBindingToken: String?,
  ) {
    if (!callbackBindingEnabled) {
      return
    }
    val expectedToken =
        verifiedState.attributes[callbackBindingStateAttributeKey]?.trim()?.takeIf {
          it.isNotBlank()
        }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding state is missing.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    val actualToken =
        callbackBindingToken?.trim()?.takeIf { it.isNotBlank() }
            ?: throw OauthRedirectRequestException(
                message = "OAuth callback binding cookie is missing.",
                errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
            )
    if (actualToken != expectedToken) {
      throw OauthRedirectRequestException(
          message = "OAuth callback binding token mismatch.",
          errorCode = OauthRedirectErrorCode.OAUTH_CALLBACK_BINDING_INVALID,
      )
    }
  }

  fun readRedirectUri(verifiedState: OauthVerifiedState): String {
    return verifiedState.redirectUri
        ?: throw OauthRedirectRequestException(
            message = "State does not include redirect_uri.",
            errorCode = OauthRedirectErrorCode.OAUTH_STATE_INVALID,
        )
  }

  fun resolveRelayCodeQueryParameterName(relayCodeQueryParameterName: String): String {
    val key = relayCodeQueryParameterName.trim()
    if (key.isBlank()) {
      throw IllegalStateException(
          "atomic.app.oauth.redirect.relay-code-query-parameter-name must not be blank.",
      )
    }
    return key
  }

  fun appendQueryParameter(
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

  fun toRelayPayload(
      provider: OauthProviderName,
      tokenResult: OauthTokenResult,
      verifiedState: OauthVerifiedState,
      internalStateAttributeKeys: Set<String>,
      resolvedIdentity: OauthIdentityResult? = null,
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
        nonce = verifiedState.nonce,
        stateAttributes = verifiedState.attributes.filterKeys { it !in internalStateAttributeKeys },
        resolvedIdentity = resolvedIdentity,
    )
  }

  fun requiresPkceCodeVerifier(verifiedState: OauthVerifiedState): Boolean {
    return verifiedState.attributes[INTERNAL_PKCE_REQUIRED_ATTRIBUTE_KEY]
        ?.trim()
        ?.equals("true", ignoreCase = true) == true
  }

  fun extractQueryParameter(
      url: String,
      parameterName: String,
  ): String? {
    val rawQuery = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
    return rawQuery
        .split('&')
        .asSequence()
        .mapNotNull { token ->
          val separatorIndex = token.indexOf('=')
          if (separatorIndex < 0) {
            return@mapNotNull null
          }
          val key =
              java.net.URLDecoder.decode(token.substring(0, separatorIndex), StandardCharsets.UTF_8)
          if (key != parameterName) {
            return@mapNotNull null
          }
          java.net.URLDecoder.decode(token.substring(separatorIndex + 1), StandardCharsets.UTF_8)
        }
        .firstOrNull()
  }

  fun internalStateAttributeKeys(callbackBindingStateAttributeKey: String): Set<String> {
    return buildSet {
      add(INTERNAL_PKCE_REQUIRED_ATTRIBUTE_KEY)
      callbackBindingStateAttributeKey.trim().takeIf { it.isNotBlank() }?.let(::add)
    }
  }

  data class ResolvedPkce(
      val codeVerifier: String,
      val codeChallenge: String,
      val codeChallengeMethod: OauthCodeChallengeMethod,
  )
}
