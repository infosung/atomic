package com.infosung.atomic.starter.autoconfigure.oauth2

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.idtoken.IdTokenParser
import com.infosung.atomic.oauth.provider.apple.AppleOauthProvider
import com.infosung.atomic.oauth.provider.google.GoogleOauthProvider
import com.infosung.atomic.oauth.provider.kakao.KakaoOauthProvider
import com.infosung.atomic.oauth.state.InMemoryOauthStateStore
import com.infosung.atomic.oauth.state.OauthStateManager
import com.infosung.atomic.oauth.state.OauthStateStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

/** Auto-configuration for atomic oauth2 shared infrastructure. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.oauth.state.OauthStateManager",
            "com.infosung.atomic.oauth.api.OauthServiceProvider",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.oauth2",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(AtomicOauth2Properties::class)
class AtomicOauth2AutoConfiguration {
  /** Registers shared OAuth [RestClient]. */
  @Bean("atomicOauthRestClient")
  @ConditionalOnMissingBean(name = ["atomicOauthRestClient"])
  @ConditionalOnClass(name = ["org.springframework.web.client.RestClient"])
  fun atomicOauthRestClient(builderProvider: ObjectProvider<RestClient.Builder>): RestClient =
      builderProvider.getIfAvailable { RestClient.builder() }.build()

  /** Registers default in-memory one-time state store. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.oauth2.state.in-memory-store",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun oauthStateStore(properties: AtomicOauth2Properties): OauthStateStore {
    return InMemoryOauthStateStore(cleanupInterval = properties.state.inMemoryStore.cleanupInterval)
  }

  /** Registers signed oauth state manager when signing secret is configured. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.oauth2.state",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  @ConditionalOnProperty(prefix = "atomic.oauth2.state", name = ["signing-secret"])
  fun oauthStateManager(
      properties: AtomicOauth2Properties,
      storeProvider: ObjectProvider<OauthStateStore>,
  ): OauthStateManager {
    val signingSecret =
        properties.state.signingSecret?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("atomic.oauth2.state.signing-secret must not be blank.")

    return OauthStateManager(
        signingSecret = signingSecret,
        issuer = properties.state.issuer,
        ttlSeconds = properties.state.ttlSeconds,
        store = storeProvider.getIfAvailable(),
        maxAttributesEntryCount = properties.state.maxAttributesEntryCount,
        maxAttributesBytes = properties.state.maxAttributesBytes,
        maxStateTokenLength = properties.state.maxStateTokenLength,
    )
  }

  /** Registers Google oauth provider (single or routed multi-client) from properties. */
  @Bean("googleOauthProvider")
  @ConditionalOnMissingBean(name = ["googleOauthProvider"])
  @ConditionalOnBean(OauthStateManager::class)
  @ConditionalOnProperty(
      prefix = "atomic.oauth2.providers.google",
      name = ["enabled"],
      havingValue = "true",
  )
  fun googleOauthProvider(
      properties: AtomicOauth2Properties,
      @Qualifier("atomicOauthRestClient") restClient: RestClient,
      oauthStateManager: OauthStateManager,
  ): OauthProvider {
    val google = properties.providers.google
    val resolvedClients = resolveGoogleClients(google)
    val defaultClientKey =
        resolveDefaultClientKey(
            configuredDefaultKey = google.defaultClientKey,
            availableClientKeys = resolvedClients.keys,
            providerLabel = "google",
        )
    val routeAttributeKey = google.routeAttributeKey.ifBlank { "atomicClientKey" }
    val supportedScopes = google.supportedScopes.takeIf { it.isNotEmpty() }?.toSet()
    val allowedIssuers = google.verifierIssuers.filter { it.isNotBlank() }.toSet()

    val providersByClientKey =
        resolvedClients.mapValues { (_, client) ->
          GoogleOauthProvider(
              clientId = client.clientId,
              clientSecret = client.clientSecret,
              serverRedirectUri = client.serverRedirectUri,
              authorizationGrantType = google.authorizationGrantType,
              client = restClient,
              googleIdTokenVerifier =
                  buildGoogleIdTokenVerifier(
                      allowedAudiences = client.allowedAudiences,
                      allowedIssuers = allowedIssuers,
                  ),
              stateManager = oauthStateManager,
              defaultScopes = google.defaultScopes.toSet(),
              supportedScopes = supportedScopes,
              userInfoEndpoint = google.userInfoEndpoint,
              allowedAudiences = client.allowedAudiences,
              requireNonceValidation = google.requireNonceValidation,
          )
        }

    if (providersByClientKey.size == 1) {
      return providersByClientKey.getValue(defaultClientKey)
    }
    val audiencesByClientKey = resolvedClients.mapValues { (_, client) -> client.allowedAudiences }
    validateAudienceUniqueness(
        providerLabel = "google",
        routeAttributeKey = routeAttributeKey,
        audiencesByClientKey = audiencesByClientKey,
    )

    return RoutedOauthProvider(
        providerName = OauthProviderName.GOOGLE,
        providersByClientKey = providersByClientKey,
        defaultClientKey = defaultClientKey,
        routeAttributeKey = routeAttributeKey,
        oauthStateManager = oauthStateManager,
        audiencesByClientKey = audiencesByClientKey,
    )
  }

  /** Registers Kakao oauth provider (single or routed multi-client) from properties. */
  @Bean("kakaoOauthProvider")
  @ConditionalOnMissingBean(name = ["kakaoOauthProvider"])
  @ConditionalOnBean(OauthStateManager::class)
  @ConditionalOnProperty(
      prefix = "atomic.oauth2.providers.kakao",
      name = ["enabled"],
      havingValue = "true",
  )
  fun kakaoOauthProvider(
      properties: AtomicOauth2Properties,
      @Qualifier("atomicOauthRestClient") restClient: RestClient,
      oauthStateManager: OauthStateManager,
  ): OauthProvider {
    val kakao = properties.providers.kakao
    val resolvedClients = resolveKakaoClients(kakao)
    val defaultClientKey =
        resolveDefaultClientKey(
            configuredDefaultKey = kakao.defaultClientKey,
            availableClientKeys = resolvedClients.keys,
            providerLabel = "kakao",
        )
    val routeAttributeKey = kakao.routeAttributeKey.ifBlank { "atomicClientKey" }
    val supportedScopes = kakao.supportedScopes.takeIf { it.isNotEmpty() }?.toSet()

    val providersByClientKey =
        resolvedClients.mapValues { (_, client) ->
          KakaoOauthProvider(
              client = restClient,
              clientId = client.clientId,
              clientSecret = client.clientSecret,
              serverRedirectUri = client.serverRedirectUri,
              idTokenParser =
                  IdTokenParser(
                      iss = kakao.idTokenIssuer,
                      allowedAudiences = client.allowedAudiences,
                      jwkSetUri = kakao.idTokenJwkSetUri,
                  ),
              stateManager = oauthStateManager,
              defaultScopes = kakao.defaultScopes.toSet(),
              supportedScopes = supportedScopes,
              userInfoEndpoint = kakao.userInfoEndpoint,
              requireNonceValidation = kakao.requireNonceValidation,
          )
        }

    if (providersByClientKey.size == 1) {
      return providersByClientKey.getValue(defaultClientKey)
    }
    val audiencesByClientKey = resolvedClients.mapValues { (_, client) -> client.allowedAudiences }
    validateAudienceUniqueness(
        providerLabel = "kakao",
        routeAttributeKey = routeAttributeKey,
        audiencesByClientKey = audiencesByClientKey,
    )

    return RoutedOauthProvider(
        providerName = OauthProviderName.KAKAO,
        providersByClientKey = providersByClientKey,
        defaultClientKey = defaultClientKey,
        routeAttributeKey = routeAttributeKey,
        oauthStateManager = oauthStateManager,
        audiencesByClientKey = audiencesByClientKey,
    )
  }

  /** Registers Apple oauth provider (single or routed multi-client) from properties. */
  @Bean("appleOauthProvider")
  @ConditionalOnMissingBean(name = ["appleOauthProvider"])
  @ConditionalOnBean(OauthStateManager::class)
  @ConditionalOnProperty(
      prefix = "atomic.oauth2.providers.apple",
      name = ["enabled"],
      havingValue = "true",
  )
  fun appleOauthProvider(
      properties: AtomicOauth2Properties,
      oauthStateManager: OauthStateManager,
  ): OauthProvider {
    val apple = properties.providers.apple
    val resolvedClients = resolveAppleClients(apple)
    val defaultClientKey =
        resolveDefaultClientKey(
            configuredDefaultKey = apple.defaultClientKey,
            availableClientKeys = resolvedClients.keys,
            providerLabel = "apple",
        )
    val routeAttributeKey = apple.routeAttributeKey.ifBlank { "atomicClientKey" }

    val providersByClientKey =
        resolvedClients.mapValues { (_, client) ->
          AppleOauthProvider(
              clientId = client.clientId,
              serverRedirectUri = client.serverRedirectUri,
              idTokenParser =
                  IdTokenParser(
                      iss = apple.idTokenIssuer,
                      allowedAudiences = client.allowedAudiences,
                      jwkSetUri = apple.idTokenJwkSetUri,
                  ),
              stateManager = oauthStateManager,
              defaultScopes = apple.defaultScopes.toSet(),
              requireNonceValidation = apple.requireNonceValidation,
          )
        }

    if (providersByClientKey.size == 1) {
      return providersByClientKey.getValue(defaultClientKey)
    }
    val audiencesByClientKey = resolvedClients.mapValues { (_, client) -> client.allowedAudiences }
    validateAudienceUniqueness(
        providerLabel = "apple",
        routeAttributeKey = routeAttributeKey,
        audiencesByClientKey = audiencesByClientKey,
    )

    return RoutedOauthProvider(
        providerName = OauthProviderName.APPLE,
        providersByClientKey = providersByClientKey,
        defaultClientKey = defaultClientKey,
        routeAttributeKey = routeAttributeKey,
        oauthStateManager = oauthStateManager,
        audiencesByClientKey = audiencesByClientKey,
    )
  }

  /** Registers provider registry from discovered oauth providers. */
  @Bean
  @ConditionalOnMissingBean
  fun oauthServiceProvider(providers: ObjectProvider<List<OauthProvider>>): OauthServiceProvider {
    return OauthServiceProvider(providers.getIfAvailable { emptyList() })
  }

  private fun buildGoogleIdTokenVerifier(
      allowedAudiences: Set<String>,
      allowedIssuers: Set<String>,
  ): GoogleIdTokenVerifier {
    val builder =
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(allowedAudiences.toList())
    if (allowedIssuers.isNotEmpty()) {
      builder.setIssuers(allowedIssuers.toList())
    }
    return builder.build()
  }

  private fun resolveDefaultClientKey(
      configuredDefaultKey: String?,
      availableClientKeys: Set<String>,
      providerLabel: String,
  ): String {
    require(availableClientKeys.isNotEmpty()) {
      "No oauth clients configured for provider '$providerLabel'."
    }
    if (availableClientKeys.size == 1) {
      return availableClientKeys.first()
    }

    val defaultKey =
        configuredDefaultKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.$providerLabel.default-client-key must be set when multiple clients are configured.")
    require(availableClientKeys.contains(defaultKey)) {
      "Configured defaultClientKey '$defaultKey' is not found for provider '$providerLabel'."
    }
    return defaultKey
  }

  private fun validateAudienceUniqueness(
      providerLabel: String,
      routeAttributeKey: String,
      audiencesByClientKey: Map<String, Set<String>>,
  ) {
    val audienceOwner = linkedMapOf<String, String>()
    audiencesByClientKey.forEach { (clientKey, audiences) ->
      audiences.forEach { audience ->
        val normalizedAudience = audience.trim()
        if (normalizedAudience.isBlank()) {
          return@forEach
        }
        val existingClientKey = audienceOwner.putIfAbsent(normalizedAudience, clientKey)
        require(existingClientKey == null || existingClientKey == clientKey) {
          "Duplicate audience '$normalizedAudience' for provider '$providerLabel' across clients " +
              "'$existingClientKey' and '$clientKey'. Configure unique audiences per client. " +
              "Use '$routeAttributeKey' to route non-identity operations."
        }
      }
    }
  }

  private fun resolveGoogleClients(
      google: AtomicOauth2Properties.Google,
  ): Map<String, GoogleClientResolved> {
    if (google.clients.isNotEmpty()) {
      return google.clients.entries.associate { (clientKey, client) ->
        require(clientKey.isNotBlank()) { "google clients key must not be blank." }
        val clientId =
            client.clientId?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.google.clients.$clientKey.client-id must not be blank.")
        val clientSecret =
            client.clientSecret?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.google.clients.$clientKey.client-secret must not be blank.")
        val serverRedirectUri =
            client.serverRedirectUri?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.google.clients.$clientKey.server-redirect-uri must not be blank.")
        val allowedAudiences =
            normalizeAudiences(client.allowedAudiences)
                .ifEmpty { normalizeAudiences(google.allowedAudiences) }
                .ifEmpty { setOf(clientId) }
        clientKey to
            GoogleClientResolved(
                clientId = clientId,
                clientSecret = clientSecret,
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            )
      }
    }

    val clientId =
        google.clientId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.google.client-id must not be blank.")
    val clientSecret =
        google.clientSecret?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.google.client-secret must not be blank.")
    val serverRedirectUri =
        google.serverRedirectUri?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.google.server-redirect-uri must not be blank.")
    val allowedAudiences = normalizeAudiences(google.allowedAudiences).ifEmpty { setOf(clientId) }

    return mapOf(
        "default" to
            GoogleClientResolved(
                clientId = clientId,
                clientSecret = clientSecret,
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            ),
    )
  }

  private fun resolveKakaoClients(
      kakao: AtomicOauth2Properties.Kakao,
  ): Map<String, KakaoClientResolved> {
    if (kakao.clients.isNotEmpty()) {
      return kakao.clients.entries.associate { (clientKey, client) ->
        require(clientKey.isNotBlank()) { "kakao clients key must not be blank." }
        val clientId =
            client.clientId?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.kakao.clients.$clientKey.client-id must not be blank.")
        val serverRedirectUri =
            client.serverRedirectUri?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.kakao.clients.$clientKey.server-redirect-uri must not be blank.")
        val allowedAudiences =
            normalizeAudiences(client.idTokenAllowedAudiences)
                .ifEmpty { normalizeAudiences(kakao.idTokenAllowedAudiences) }
                .ifEmpty { setOf(clientId) }
        clientKey to
            KakaoClientResolved(
                clientId = clientId,
                clientSecret = client.clientSecret?.takeIf { it.isNotBlank() },
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            )
      }
    }

    val clientId =
        kakao.clientId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.kakao.client-id must not be blank.")
    val serverRedirectUri =
        kakao.serverRedirectUri?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.kakao.server-redirect-uri must not be blank.")
    val allowedAudiences =
        normalizeAudiences(kakao.idTokenAllowedAudiences).ifEmpty { setOf(clientId) }

    return mapOf(
        "default" to
            KakaoClientResolved(
                clientId = clientId,
                clientSecret = kakao.clientSecret?.takeIf { it.isNotBlank() },
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            ),
    )
  }

  private fun resolveAppleClients(
      apple: AtomicOauth2Properties.Apple,
  ): Map<String, AppleClientResolved> {
    if (apple.clients.isNotEmpty()) {
      return apple.clients.entries.associate { (clientKey, client) ->
        require(clientKey.isNotBlank()) { "apple clients key must not be blank." }
        val clientId =
            client.clientId?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.apple.clients.$clientKey.client-id must not be blank.")
        val serverRedirectUri =
            client.serverRedirectUri?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException(
                    "atomic.oauth2.providers.apple.clients.$clientKey.server-redirect-uri must not be blank.")
        val allowedAudiences =
            normalizeAudiences(client.idTokenAllowedAudiences)
                .ifEmpty { normalizeAudiences(apple.idTokenAllowedAudiences) }
                .ifEmpty { setOf(clientId) }
        clientKey to
            AppleClientResolved(
                clientId = clientId,
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            )
      }
    }

    val clientId =
        apple.clientId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.apple.client-id must not be blank.")
    val serverRedirectUri =
        apple.serverRedirectUri?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.oauth2.providers.apple.server-redirect-uri must not be blank.")
    val allowedAudiences =
        normalizeAudiences(apple.idTokenAllowedAudiences).ifEmpty { setOf(clientId) }

    return mapOf(
        "default" to
            AppleClientResolved(
                clientId = clientId,
                serverRedirectUri = serverRedirectUri,
                allowedAudiences = allowedAudiences,
            ),
    )
  }

  private data class GoogleClientResolved(
      val clientId: String,
      val clientSecret: String,
      val serverRedirectUri: String,
      val allowedAudiences: Set<String>,
  )

  private data class KakaoClientResolved(
      val clientId: String,
      val clientSecret: String?,
      val serverRedirectUri: String,
      val allowedAudiences: Set<String>,
  )

  private data class AppleClientResolved(
      val clientId: String,
      val serverRedirectUri: String,
      val allowedAudiences: Set<String>,
  )

  private fun normalizeAudiences(values: Set<String>): Set<String> {
    return values.mapNotNull { it.trim().takeIf(String::isNotBlank) }.toSet()
  }
}
