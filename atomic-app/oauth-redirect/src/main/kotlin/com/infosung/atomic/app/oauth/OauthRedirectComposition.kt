package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.oauth.OauthServiceProviderAdapter
import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.AppOauthRelayCodePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.state.OauthStateManagerAdapter
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.out.IssueOauthRelayCodePort
import com.infosung.atomic.app.oauth.application.port.out.OauthProviderOperationsPort
import com.infosung.atomic.app.oauth.application.port.out.ValidateOauthRedirectUriPort
import com.infosung.atomic.app.oauth.application.port.out.VerifyOauthStatePort
import com.infosung.atomic.app.oauth.application.service.BuildAppleCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildAuthorizationRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildOauthCallbackRedirectService
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
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
      relayCodeService: AppOauthRelayCodeService,
  ): IssueOauthRelayCodePort {
    return AppOauthRelayCodePortAdapter(relayCodeService)
  }

  fun validateOauthRedirectUriPort(
      properties: AtomicAppOauthRedirectProperties,
  ): ValidateOauthRedirectUriPort {
    return AllowedRedirectUriPortAdapter(properties)
  }

  fun buildAuthorizationRedirectUseCase(
      oauthServiceProvider: OauthServiceProvider,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAuthorizationRedirectUseCase {
    return BuildAuthorizationRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort(oauthServiceProvider),
        validateOauthRedirectUriPort = validateOauthRedirectUriPort(properties),
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
    )
  }

  fun buildOauthCallbackRedirectUseCase(
      oauthServiceProvider: OauthServiceProvider,
      oauthStateManager: OauthStateManager,
      relayCodeService: AppOauthRelayCodeService,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildOauthCallbackRedirectUseCase {
    return BuildOauthCallbackRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort(oauthServiceProvider),
        verifyOauthStatePort = verifyOauthStatePort(oauthStateManager),
        issueOauthRelayCodePort = issueOauthRelayCodePort(relayCodeService),
        validateOauthRedirectUriPort = validateOauthRedirectUriPort(properties),
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
        relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
        callbackEndpointPath = properties.callbackEndpointPath,
    )
  }

  fun buildAppleCallbackRedirectUseCase(
      oauthServiceProvider: OauthServiceProvider,
      oauthStateManager: OauthStateManager,
      relayCodeService: AppOauthRelayCodeService,
      properties: AtomicAppOauthRedirectProperties,
  ): BuildAppleCallbackRedirectUseCase {
    return BuildAppleCallbackRedirectService(
        oauthProviderOperationsPort = oauthProviderOperationsPort(oauthServiceProvider),
        verifyOauthStatePort = verifyOauthStatePort(oauthStateManager),
        issueOauthRelayCodePort = issueOauthRelayCodePort(relayCodeService),
        validateOauthRedirectUriPort = validateOauthRedirectUriPort(properties),
        callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
        callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
        relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
    )
  }
}
