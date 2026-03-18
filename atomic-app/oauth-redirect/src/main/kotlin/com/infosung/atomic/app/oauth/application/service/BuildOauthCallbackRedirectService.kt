package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.OauthRedirectClientTargetClassifier
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.CallbackRedirectResult
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import java.util.Locale
import org.slf4j.LoggerFactory

internal class BuildOauthCallbackRedirectService(
    private val oauthProviderOperationsPort: OauthProviderOperationsPort,
    private val verifyOauthStatePort: VerifyOauthStatePort,
    private val issueOauthRelayCodePort: IssueOauthRelayCodePort,
    private val validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
    private val callbackBindingEnabled: Boolean,
    private val callbackBindingStateAttributeKey: String,
    private val relayCodeQueryParameterName: String,
    private val callbackEndpointPath: String = "/oauth/callback",
) : BuildOauthCallbackRedirectUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun build(
      provider: String,
      code: String,
      state: String,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String?,
  ): CallbackRedirectResult {
    val providerName = parseProviderName(provider)
    if (providerName == OauthProviderName.APPLE) {
      throw HttpStatusException(
          status = 400,
          message = "Use POST ${resolveAppleCallbackPath()} for Apple callback.",
      )
    }
    val stateJwt =
        verifyOauthStatePort.verifyState(
            signedState = state,
            expectedProvider = providerName,
        )
    OauthRedirectUseCaseSupport.validateCallbackBinding(
        stateJwt = stateJwt,
        callbackBindingEnabled = callbackBindingEnabled,
        callbackBindingStateAttributeKey = callbackBindingStateAttributeKey,
        callbackBindingToken = callbackBindingToken,
    )
    val tokenExchange =
        oauthProviderOperationsPort.exchangeCode(
            provider = provider,
            request =
                OauthTokenExchangeRequest(
                    code = code,
                    state = state,
                    additionalParameters = additionalParameters,
                ),
        )
    val redirectUri =
        validateOauthRedirectUriPort.validateRedirectUri(
            OauthRedirectUseCaseSupport.readRedirectUri(stateJwt),
        )
    val relayCode =
        issueOauthRelayCodePort.issueRelayCode(
            OauthRedirectUseCaseSupport.toRelayPayload(
                provider = tokenExchange.providerName,
                tokenResult = tokenExchange.tokenResult,
                stateJwt = stateJwt,
            ),
        )
    val queryParameterName =
        OauthRedirectUseCaseSupport.resolveRelayCodeQueryParameterName(relayCodeQueryParameterName)
    val frontendRedirectUrl =
        OauthRedirectUseCaseSupport.appendQueryParameter(
            url = redirectUri,
            key = queryParameterName,
            value = relayCode,
        )
    val redirectTargetType = OauthRedirectClientTargetClassifier.classify(redirectUri)
    log.debug(
        "Built oauth callback frontend redirect via use-case: provider={}, redirectUri={}, redirectTargetType={}, relayCodeLength={}, additionalParameterKeys={}",
        tokenExchange.providerName,
        redirectUri,
        redirectTargetType,
        relayCode.length,
        additionalParameters.keys.sorted(),
    )
    return CallbackRedirectResult(
        providerName = tokenExchange.providerName,
        frontendRedirectUrl = frontendRedirectUrl,
        redirectTargetType = redirectTargetType,
        relayCodeLength = relayCode.length,
    )
  }

  private fun resolveAppleCallbackPath(): String {
    val normalizedPath = callbackEndpointPath.trim().ifBlank { "/oauth/callback" }
    return "$normalizedPath/apple"
  }

  private fun parseProviderName(provider: String): OauthProviderName {
    return runCatching { OauthProviderName.valueOf(provider.uppercase(Locale.ROOT)) }
        .getOrElse {
          throw HttpStatusException(status = 400, message = "Unsupported provider: $provider")
        }
  }
}
