package com.infosung.atomic.app.oauth.autoconfigure

import com.infosung.atomic.app.oauth.adapter.out.redirect.AllowedRedirectUriPolicySupport
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.state.OauthStateStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/** Stable umbrella auto-configuration entrypoint for the app OAuth redirect module. */
@AutoConfiguration(
    afterName = ["com.infosung.atomic.starter.autoconfigure.oauth2.AtomicOauth2AutoConfiguration"])
@ConditionalOnClass(
    name =
        [
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.GetMapping",
            "com.infosung.atomic.oauth.api.OauthServiceProvider",
            "com.infosung.atomic.oauth.state.OauthStateManager",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.oauth.redirect",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
@Import(
    AtomicAppOauthRedirectRelayAutoConfiguration::class,
    AtomicAppOauthRedirectCoreAutoConfiguration::class,
    AtomicAppOauthRedirectWebAutoConfiguration::class,
)
class AtomicAppOauthRedirectAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  private enum class StateStoreSummaryType {
    ABSENT,
    OPAQUE_REPLAY_PROTECTED,
    MULTIPLE_CANDIDATES,
    IN_MEMORY,
    CUSTOM_OR_SHARED,
  }

  @Bean
  fun appOauthRedirectPropertiesValidator(
      properties: AtomicAppOauthRedirectProperties,
      oauthServiceProviderProvider: ObjectProvider<OauthServiceProvider>,
      oauthStateManagerProvider: ObjectProvider<OauthStateManager>,
      oauthStateStoreProvider: ObjectProvider<OauthStateStore>,
  ): Any {
    validateGlobalProperties(properties)
    validateSecurityProperties(properties)
    val oauthStateManager =
        validateRequiredOauthBeans(
            oauthServiceProviderProvider = oauthServiceProviderProvider,
            oauthStateManagerProvider = oauthStateManagerProvider,
        )
    logDeploymentSummary(
        properties = properties,
        oauthStateManager = oauthStateManager,
        oauthStateStoreProvider = oauthStateStoreProvider,
    )
    log.debug(
        "Validated oauth redirect auto-configuration prerequisites: storeType={}, callbackBindingMode={}, replayProtectionEnabled={}",
        properties.store.type,
        properties.callbackBinding.resolvedMode(),
        oauthStateManager.isReplayProtectionEnabled(),
    )
    return Any()
  }

  private fun validateSecurityProperties(properties: AtomicAppOauthRedirectProperties) {
    AllowedRedirectUriPolicySupport.validateConfiguredPrefixes(
        properties.allowedRedirectUriPrefixes)

    if (!properties.callbackBinding.isCookieValidationEnabled()) {
      return
    }
    require(properties.callbackBinding.stateAttributeKey.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.state-attribute-key must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieName.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-name must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieName.startsWith("__Host-")) {
      "atomic.app.oauth.redirect.callback-binding.cookie-name must start with '__Host-' when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieSameSite.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-same-site must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookiePath.isNotBlank()) {
      "atomic.app.oauth.redirect.callback-binding.cookie-path must not be blank when callback binding is enabled."
    }
    require(properties.callbackBinding.cookiePath.trim() == "/") {
      "atomic.app.oauth.redirect.callback-binding.cookie-path must be '/' when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieSecure) {
      "atomic.app.oauth.redirect.callback-binding.cookie-secure must be true when callback binding is enabled."
    }
    require(properties.callbackBinding.cookieMaxAgeSeconds > 0) {
      "atomic.app.oauth.redirect.callback-binding.cookie-max-age-seconds must be greater than zero when callback binding is enabled."
    }
  }

  private fun validateGlobalProperties(properties: AtomicAppOauthRedirectProperties) {
    require(properties.relayCodeTtlSeconds > 0) {
      "atomic.app.oauth.redirect.relay-code-ttl-seconds must be greater than zero."
    }
  }

  private fun validateRequiredOauthBeans(
      oauthServiceProviderProvider: ObjectProvider<OauthServiceProvider>,
      oauthStateManagerProvider: ObjectProvider<OauthStateManager>,
  ): OauthStateManager {
    val oauthServiceProvider = oauthServiceProviderProvider.getIfAvailable()
    val oauthStateManager = oauthStateManagerProvider.getIfAvailable()
    if (oauthServiceProvider != null &&
        oauthStateManager != null &&
        oauthStateManager.isReplayProtectionEnabled()) {
      return oauthStateManager
    }
    val message =
        "atomic.app.oauth.redirect.enabled=true requires OauthServiceProvider and store-backed OauthStateManager beans."
    log.error(
        "{} providerPresent={}, stateManagerPresent={}, replayProtectionEnabled={}",
        message,
        oauthServiceProvider != null,
        oauthStateManager != null,
        oauthStateManager?.isReplayProtectionEnabled() == true,
    )
    throw IllegalStateException(message)
  }

  private fun logDeploymentSummary(
      properties: AtomicAppOauthRedirectProperties,
      oauthStateManager: OauthStateManager,
      oauthStateStoreProvider: ObjectProvider<OauthStateStore>,
  ) {
    val callbackBindingMode = properties.callbackBinding.resolvedMode()
    val stateStoreType =
        resolveStateStoreType(
            oauthStateManager = oauthStateManager,
            oauthStateStoreProvider = oauthStateStoreProvider,
        )

    log.info(
        "OAuth redirect deployment summary: relayStoreType={}, relayStoreFailFast={}, callbackBindingMode={}, replayProtectionEnabled={}, stateStoreType={}",
        properties.store.type,
        properties.store.failFast,
        callbackBindingMode,
        oauthStateManager.isReplayProtectionEnabled(),
        stateStoreType.name,
    )

    if (properties.store.type == AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY) {
      log.warn(
          "OAuth redirect relay store is configured as in-memory. It is process-local per instance and fits only local or intentionally single-node deployments.")
    }
    if (properties.store.type != AtomicAppOauthRedirectProperties.StoreType.IN_MEMORY &&
        !properties.store.failFast) {
      log.warn(
          "OAuth redirect relay store fail-fast is disabled for configuredStoreType={}. Startup fallback can switch to the in-memory relay store, which is process-local per instance.",
          properties.store.type,
      )
    }
    if (stateStoreType == StateStoreSummaryType.IN_MEMORY) {
      log.warn(
          "OAuth state replay protection uses the in-memory state store. It is process-local per instance and is not suitable for multi-instance deployments.")
    }
    when (callbackBindingMode) {
      AtomicAppOauthRedirectProperties.CallbackBindingMode.DISABLED ->
          log.warn(
              "OAuth callback binding mode is disabled. Use this only for local HTTP-only testing or other explicitly trusted environments.")

      AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED ->
          log.info(
              "OAuth callback binding mode is relaxed. Cookie reuse is allowed after successful callbacks; verify the UX and security tradeoff intentionally.")

      AtomicAppOauthRedirectProperties.CallbackBindingMode.STRICT -> Unit
    }
  }

  private fun resolveStateStoreType(
      oauthStateManager: OauthStateManager,
      oauthStateStoreProvider: ObjectProvider<OauthStateStore>,
  ): StateStoreSummaryType {
    if (!oauthStateManager.isReplayProtectionEnabled()) {
      return StateStoreSummaryType.ABSENT
    }
    val stateStores = oauthStateStoreProvider.orderedStream().limit(2).toList()
    return when {
      stateStores.isEmpty() -> StateStoreSummaryType.OPAQUE_REPLAY_PROTECTED
      stateStores.size > 1 -> StateStoreSummaryType.MULTIPLE_CANDIDATES
      stateStores.first() is InMemoryOauthStateStore -> StateStoreSummaryType.IN_MEMORY
      else -> StateStoreSummaryType.CUSTOM_OR_SHARED
    }
  }
}
