package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.out.oauth.OauthServiceProviderAdapter
import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.IssueOauthRelayCodeUseCasePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.state.OauthStateManagerAdapter
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.application.service.BuildAppleCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildAuthorizationRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildOauthCallbackRedirectService
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.OauthStateManager

internal object OauthRedirectComposition {
  fun oauthProviderOperationsPort(
      oauthServiceProvider: OauthServiceProvider,
  ): OauthProviderOperationsPort {
    return OauthServiceProviderAdapter(oauthServiceProvider)
  }

  fun verifyOauthStatePort(
      oauthStateManager: OauthStateManager,
  ): VerifyOauthStatePort {
    return OauthStateManagerAdapter(oauthStateManager)
  }

  fun issueOauthRelayCodePort(
      issueOauthRelayCodeUseCase: IssueOauthRelayCodeUseCase,
  ): IssueOauthRelayCodePort {
    return IssueOauthRelayCodeUseCasePortAdapter(issueOauthRelayCodeUseCase)
  }

  fun validateOauthRedirectUriPort(
      properties: AtomicAppOauthRedirectProperties,
  ): ValidateOauthRedirectUriPort {
    return AllowedRedirectUriPortAdapter(properties)
  }

  fun buildAuthorizationRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAuthorizationRedirectUseCase {
    return BuildAuthorizationRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
    )
  }

  fun buildOauthCallbackRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      verifyOauthStatePort: VerifyOauthStatePort,
      issueOauthRelayCodePort: IssueOauthRelayCodePort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildOauthCallbackRedirectUseCase {
    return BuildOauthCallbackRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        verifyOauthStatePort = verifyOauthStatePort,
        issueOauthRelayCodePort = issueOauthRelayCodePort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
        relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
        callbackEndpointPath = properties.callbackEndpointPath,
    )
  }

  fun buildAppleCallbackRedirectUseCase(
      oauthProviderOperationsPort: OauthProviderOperationsPort,
      verifyOauthStatePort: VerifyOauthStatePort,
      issueOauthRelayCodePort: IssueOauthRelayCodePort,
      validateOauthRedirectUriPort: ValidateOauthRedirectUriPort,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAppleCallbackRedirectUseCase {
    return BuildAppleCallbackRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort,
        verifyOauthStatePort = verifyOauthStatePort,
        issueOauthRelayCodePort = issueOauthRelayCodePort,
        validateOauthRedirectUriPort = validateOauthRedirectUriPort,
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
        relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
    )
  }
}
