package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.adapter.out.redirect.OauthRedirectClientTargetClassifier
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.CallbackRedirectResult
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
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
      throw OauthRedirectRequestException(
          "Use POST ${resolveAppleCallbackPath()} for Apple callback.")
    }
    val verifiedState =
        verifyOauthStatePort.verifyState(
            signedState = state,
            expectedProvider = providerName,
        )
    OauthRedirectUseCaseSupport.validateCallbackBinding(
        verifiedState = verifiedState,
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
            OauthRedirectUseCaseSupport.readRedirectUri(verifiedState),
        )
    val relayCode =
        issueOauthRelayCodePort.issueRelayCode(
            OauthRedirectUseCaseSupport.toRelayPayload(
                provider = tokenExchange.providerName,
                tokenResult = tokenExchange.tokenResult,
                verifiedState = verifiedState,
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
        .getOrElse { throw OauthRedirectRequestException("Unsupported provider: $provider") }
  }
}
