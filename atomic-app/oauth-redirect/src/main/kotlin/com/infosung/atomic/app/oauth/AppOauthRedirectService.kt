package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.out.oauth.OauthServiceProviderAdapter
import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPortAdapter
import com.infosung.atomic.app.oauth.adapter.out.relay.AppOauthRelayCodePortAdapter
import com.infosung.atomic.app.oauth.adapter.out.state.OauthStateManagerAdapter
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectApplicationException
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.service.BuildAppleCallbackRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildAuthorizationRedirectService
import com.infosung.atomic.app.oauth.application.service.BuildOauthCallbackRedirectService
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.exception.OauthException
import com.infosung.atomic.oauth.state.OauthStateManager
import org.slf4j.LoggerFactory

/** Redirect/callback orchestration for OAuth relayCode flow. */
class AppOauthRedirectService
private constructor(
    private val properties: AtomicAppOauthRedirectProperties,
    private val buildAuthorizationRedirectUseCase: BuildAuthorizationRedirectUseCase,
    private val buildOauthCallbackRedirectUseCase: BuildOauthCallbackRedirectUseCase,
    private val buildAppleCallbackRedirectUseCase: BuildAppleCallbackRedirectUseCase,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  constructor(
      oauthServiceProvider: OauthServiceProvider,
      oauthStateManager: OauthStateManager,
      relayCodeService: AppOauthRelayCodeService,
      properties: AtomicAppOauthRedirectProperties,
  ) : this(
      properties = properties,
      buildAuthorizationRedirectUseCase =
          defaultBuildAuthorizationRedirectUseCase(
              oauthServiceProvider = oauthServiceProvider,
              properties = properties,
          ),
      buildOauthCallbackRedirectUseCase =
          defaultBuildOauthCallbackRedirectUseCase(
              oauthServiceProvider = oauthServiceProvider,
              oauthStateManager = oauthStateManager,
              relayCodeService = relayCodeService,
              properties = properties,
          ),
      buildAppleCallbackRedirectUseCase =
          defaultBuildAppleCallbackRedirectUseCase(
              oauthServiceProvider = oauthServiceProvider,
              oauthStateManager = oauthStateManager,
              relayCodeService = relayCodeService,
              properties = properties,
          ),
  ) {
    log.debug(
        "Configured oauth redirect facade with default application composition: providerRegistryType={}, stateManagerType={}, relayCodeServiceType={}",
        oauthServiceProvider::class.java.name,
        oauthStateManager::class.java.name,
        relayCodeService::class.java.name,
    )
  }

  internal constructor(
      oauthServiceProvider: OauthServiceProvider,
      oauthStateManager: OauthStateManager,
      relayCodeService: AppOauthRelayCodeService,
      properties: AtomicAppOauthRedirectProperties,
      buildAuthorizationRedirectUseCase: BuildAuthorizationRedirectUseCase,
      buildOauthCallbackRedirectUseCase: BuildOauthCallbackRedirectUseCase,
      buildAppleCallbackRedirectUseCase: BuildAppleCallbackRedirectUseCase,
  ) : this(
      properties = properties,
      buildAuthorizationRedirectUseCase = buildAuthorizationRedirectUseCase,
      buildOauthCallbackRedirectUseCase = buildOauthCallbackRedirectUseCase,
      buildAppleCallbackRedirectUseCase = buildAppleCallbackRedirectUseCase,
  ) {
    log.debug(
        "Configured oauth redirect facade with injected application composition: authorizationUseCase={}, callbackUseCase={}, appleCallbackUseCase={}",
        buildAuthorizationRedirectUseCase::class.java.name,
        buildOauthCallbackRedirectUseCase::class.java.name,
        buildAppleCallbackRedirectUseCase::class.java.name,
    )
  }

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
    val result =
        mapApplicationErrors(provider = provider, action = "oauth authorization redirect") {
          buildAuthorizationRedirectUseCase.build(
              provider = provider,
              redirectUri = redirectUri,
              nonce = nonce,
              prompt = prompt,
              loginHint = loginHint,
              responseMode = responseMode,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken,
          )
        }
    log.debug(
        "Built oauth authorization URL through facade: provider={}, redirectTargetType={}",
        result.providerName,
        result.redirectTargetType,
    )
    return result.authorizationUrl
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
      val result =
          buildOauthCallbackRedirectUseCase.build(
              provider = provider,
              code = code,
              state = state,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken,
          )
      log.debug(
          "OAuth callback processed through facade: provider={}, redirectTargetType={}, relayCodeLength={}",
          result.providerName,
          result.redirectTargetType,
          result.relayCodeLength,
      )
      result.frontendRedirectUrl
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
      val result =
          buildAppleCallbackRedirectUseCase.build(
              state = state,
              idToken = idToken,
              code = code,
              user = user,
              additionalParameters = additionalParameters,
              callbackBindingToken = callbackBindingToken,
          )
      log.debug(
          "Apple oauth callback processed through facade: redirectTargetType={}, relayCodeLength={}",
          result.redirectTargetType,
          result.relayCodeLength,
      )
      result.frontendRedirectUrl
    }
  }

  private inline fun <T> mapCallbackErrors(
      provider: String,
      block: () -> T,
  ): T {
    return try {
      block()
    } catch (e: OauthRedirectApplicationException) {
      log.warn("Rejected oauth callback: provider={}, message={}", provider, e.message)
      throw HttpStatusException(
          status = 400,
          message = e.message ?: "Invalid OAuth callback request for provider: $provider",
          cause = e,
      )
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

  private inline fun <T> mapApplicationErrors(
      provider: String,
      action: String,
      block: () -> T,
  ): T {
    return try {
      block()
    } catch (e: OauthRedirectApplicationException) {
      log.warn("Rejected {}: provider={}, message={}", action, provider, e.message)
      throw HttpStatusException(
          status = 400,
          message = e.message ?: "Invalid request for provider: $provider",
          cause = e,
      )
    } catch (e: HttpStatusException) {
      log.warn("Rejected {}: provider={}, message={}", action, provider, e.message)
      throw e
    }
  }

  companion object {
    private fun defaultBuildAuthorizationRedirectUseCase(
        oauthServiceProvider: OauthServiceProvider,
        properties: AtomicAppOauthRedirectProperties,
    ): BuildAuthorizationRedirectUseCase {
      return BuildAuthorizationRedirectService(
          oauthProviderOperationsPort = OauthServiceProviderAdapter(oauthServiceProvider),
          validateOauthRedirectUriPort = AllowedRedirectUriPortAdapter(properties),
          callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
          callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
      )
    }

    private fun defaultBuildOauthCallbackRedirectUseCase(
        oauthServiceProvider: OauthServiceProvider,
        oauthStateManager: OauthStateManager,
        relayCodeService: AppOauthRelayCodeService,
        properties: AtomicAppOauthRedirectProperties,
    ): BuildOauthCallbackRedirectUseCase {
      return BuildOauthCallbackRedirectService(
          oauthProviderOperationsPort = OauthServiceProviderAdapter(oauthServiceProvider),
          verifyOauthStatePort = OauthStateManagerAdapter(oauthStateManager),
          issueOauthRelayCodePort = AppOauthRelayCodePortAdapter(relayCodeService),
          validateOauthRedirectUriPort = AllowedRedirectUriPortAdapter(properties),
          callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
          callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
          relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
          callbackEndpointPath = properties.callbackEndpointPath,
      )
    }

    private fun defaultBuildAppleCallbackRedirectUseCase(
        oauthServiceProvider: OauthServiceProvider,
        oauthStateManager: OauthStateManager,
        relayCodeService: AppOauthRelayCodeService,
        properties: AtomicAppOauthRedirectProperties,
    ): BuildAppleCallbackRedirectUseCase {
      return BuildAppleCallbackRedirectService(
          oauthProviderOperationsPort = OauthServiceProviderAdapter(oauthServiceProvider),
          verifyOauthStatePort = OauthStateManagerAdapter(oauthStateManager),
          issueOauthRelayCodePort = AppOauthRelayCodePortAdapter(relayCodeService),
          validateOauthRedirectUriPort = AllowedRedirectUriPortAdapter(properties),
          callbackBindingEnabled = properties.callbackBinding.isCookieValidationEnabled(),
          callbackBindingStateAttributeKey = properties.callbackBinding.stateAttributeKey.trim(),
          relayCodeQueryParameterName = properties.relayCodeQueryParameterName,
      )
    }
  }
}
