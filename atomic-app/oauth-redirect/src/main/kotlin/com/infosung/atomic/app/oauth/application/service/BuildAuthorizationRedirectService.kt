package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTargetClassifier
import com.infosung.atomic.app.oauth.application.port.`in`.AuthorizationRedirectResult
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthCodeChallengeMethod
import org.slf4j.LoggerFactory

internal class BuildAuthorizationRedirectService(
    private val oauthProviderOperationsPort: OauthProviderOperationsPort,
    private val validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
    private val callbackBindingEnabled: Boolean,
    private val callbackBindingStateAttributeKey: String,
) : BuildAuthorizationRedirectUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun build(
      provider: String,
      redirectUri: String,
      nonce: String?,
      prompt: String?,
      loginHint: String?,
      responseMode: String?,
      codeVerifier: String?,
      codeChallengeMethod: OauthCodeChallengeMethod?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String?,
  ): AuthorizationRedirectResult {
    val normalizedRedirectUri = validateOauthRedirectUriPort.validateRedirectUri(redirectUri)
    val resolvedPkce =
        OauthRedirectUseCaseSupport.resolvePkce(
            codeVerifier = codeVerifier,
            codeChallengeMethod = codeChallengeMethod,
        )
    val stateAttributes =
        OauthRedirectUseCaseSupport.buildStateAttributes(
            callbackBindingEnabled = callbackBindingEnabled,
            callbackBindingStateAttributeKey = callbackBindingStateAttributeKey,
            callbackBindingToken = callbackBindingToken,
            resolvedPkce = resolvedPkce,
        )
    val providerAuthorization =
        oauthProviderOperationsPort.buildAuthorizationUrl(
            provider = provider,
            request =
                OauthAuthorizationRequest(
                    redirectUri = normalizedRedirectUri,
                    nonce = nonce?.takeIf { it.isNotBlank() },
                    prompt = prompt?.takeIf { it.isNotBlank() },
                    loginHint = loginHint?.takeIf { it.isNotBlank() },
                    responseMode = responseMode?.takeIf { it.isNotBlank() },
                    codeChallenge = resolvedPkce?.codeChallenge,
                    codeChallengeMethod = resolvedPkce?.codeChallengeMethod,
                    stateAttributes = stateAttributes,
                    additionalParameters = additionalParameters,
                ),
        )
    val redirectTargetType = OauthRedirectClientTargetClassifier.classify(normalizedRedirectUri)
    if (log.isDebugEnabled) {
      log.debug(
          "Built oauth authorization URL via use-case: provider={}, redirectUri={}, redirectTargetType={}, hasPkce={}, additionalParameterKeys={}",
          providerAuthorization.providerName,
          normalizedRedirectUri,
          redirectTargetType,
          resolvedPkce != null,
          additionalParameters.keys.sorted(),
      )
    }
    return AuthorizationRedirectResult(
        providerName = providerAuthorization.providerName,
        authorizationUrl = providerAuthorization.authorizationUrl,
        redirectTargetType = redirectTargetType,
    )
  }
}
