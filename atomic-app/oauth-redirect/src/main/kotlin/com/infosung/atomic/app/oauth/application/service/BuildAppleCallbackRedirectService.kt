package com.infosung.atomic.app.oauth.application.service

import com.infosung.atomic.app.oauth.OauthRedirectClientTargetClassifier
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.CallbackRedirectResult
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthTokenResult
import org.slf4j.LoggerFactory

internal class BuildAppleCallbackRedirectService(
    private val oauthProviderOperationsPort: OauthProviderOperationsPort,
    private val verifyOauthStatePort: VerifyOauthStatePort,
    private val issueOauthRelayCodePort: IssueOauthRelayCodePort,
    private val validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
    private val callbackBindingEnabled: Boolean,
    private val callbackBindingStateAttributeKey: String,
    private val relayCodeQueryParameterName: String,
) : BuildAppleCallbackRedirectUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun build(
      state: String,
      idToken: String,
      code: String?,
      user: String?,
      additionalParameters: Map<String, String>,
      callbackBindingToken: String?,
  ): CallbackRedirectResult {
    val providerName = oauthProviderOperationsPort.requireProviderName(OauthProviderName.APPLE.name)
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

    val raw = linkedMapOf<String, Any?>()
    code?.takeIf { it.isNotBlank() }?.let { raw["code"] = it }
    user?.takeIf { it.isNotBlank() }?.let { raw["user"] = it }
    raw.putAll(additionalParameters)

    val redirectUri =
        validateOauthRedirectUriPort.validateRedirectUri(
            OauthRedirectUseCaseSupport.readRedirectUri(verifiedState),
        )
    val relayCode =
        issueOauthRelayCodePort.issueRelayCode(
            OauthRedirectUseCaseSupport.toRelayPayload(
                provider = providerName,
                tokenResult =
                    OauthTokenResult(
                        idToken = idToken,
                        raw = raw,
                    ),
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
        "Built Apple oauth callback frontend redirect via use-case: redirectUri={}, redirectTargetType={}, relayCodeLength={}, additionalParameterKeys={}",
        redirectUri,
        redirectTargetType,
        relayCode.length,
        additionalParameters.keys.sorted(),
    )
    return CallbackRedirectResult(
        providerName = providerName,
        frontendRedirectUrl = frontendRedirectUrl,
        redirectTargetType = redirectTargetType,
        relayCodeLength = relayCode.length,
    )
  }
}
