package com.infosung.atomic.starter.autoconfigure.security

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.channel.DefaultClientChannelResolver
import com.infosung.atomic.spring.security.config.JwtSecurityConfigurerAdapter
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper

/** Auto-configuration for atomic spring-security beans. */
@AutoConfiguration
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.spring.security.jwt.JwtProvider",
            "com.infosung.atomic.spring.security.config.JwtSecurityConfigurerAdapter",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.security",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(AtomicSecurityProperties::class)
class AtomicSecurityAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** Registers cookie policy bean for token resolvers/issuers. */
  @Bean
  @ConditionalOnMissingBean
  fun securityCookiePolicy(properties: AtomicSecurityProperties): SecurityCookiePolicy {
    return SecurityCookiePolicy(
        sameSite = properties.cookie.sameSite,
        secure = properties.cookie.secure,
        path = properties.cookie.path,
        domain = properties.cookie.domain,
    )
  }

  /** Registers default client channel resolver. */
  @Bean
  @ConditionalOnMissingBean
  fun clientChannelResolver(): ClientChannelResolver = DefaultClientChannelResolver()

  /** Registers [JwtProvider] when JWT keys are configured. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "atomic.security.jwt",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  @ConditionalOnProperty(prefix = "atomic.security.jwt", name = ["access-key", "refresh-key"])
  fun jwtProvider(
      properties: AtomicSecurityProperties,
      timeProvider: TimeProvider,
  ): JwtProvider {
    val accessKey = properties.jwt.accessKey?.takeIf { it.isNotBlank() }
    val refreshKey = properties.jwt.refreshKey?.takeIf { it.isNotBlank() }
    require(!accessKey.isNullOrBlank()) { "atomic.security.jwt.access-key must not be blank." }
    require(!refreshKey.isNullOrBlank()) { "atomic.security.jwt.refresh-key must not be blank." }

    val accessKeyId = properties.jwt.accessKeyId.trim()
    val refreshKeyId = properties.jwt.refreshKeyId.trim()
    require(accessKeyId.isNotBlank()) { "atomic.security.jwt.access-key-id must not be blank." }
    require(refreshKeyId.isNotBlank()) { "atomic.security.jwt.refresh-key-id must not be blank." }

    val previousAccessKeys =
        normalizePreviousKeys(
            raw = properties.jwt.previousAccessKeys,
            activeKeyId = accessKeyId,
            propertyName = "atomic.security.jwt.previous-access-keys",
        )
    val previousRefreshKeys =
        normalizePreviousKeys(
            raw = properties.jwt.previousRefreshKeys,
            activeKeyId = refreshKeyId,
            propertyName = "atomic.security.jwt.previous-refresh-keys",
        )

    return JwtProvider(
        accessKey = accessKey,
        refreshKey = refreshKey,
        algorithm = properties.jwt.algorithm,
        serviceName = properties.jwt.serviceName,
        accessExpiredSecond = properties.jwt.accessExpiredSecond,
        refreshExpiredSecond = properties.jwt.refreshExpiredSecond,
        timeProvider = timeProvider,
        accessKeyId = accessKeyId,
        refreshKeyId = refreshKeyId,
        previousAccessKeys = previousAccessKeys,
        previousRefreshKeys = previousRefreshKeys,
    )
  }

  /**
   * Registers [JwtSecurityConfigurerAdapter] for explicit HttpSecurity integration.
   *
   * Fail-fast policy:
   * - When security auto-config is enabled and ObjectMapper is present, this bean requires
   *   JwtProvider (auto-registered by keys or user-provided custom bean).
   * - Missing JwtProvider causes startup failure with explicit guidance.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ObjectMapper::class)
  @ConditionalOnClass(name = ["tools.jackson.databind.ObjectMapper"])
  fun jwtSecurityConfigurerAdapter(
      jwtProviderProvider: ObjectProvider<JwtProvider>,
      objectMapper: ObjectMapper,
      clientChannelResolver: ClientChannelResolver,
      cookiePolicy: SecurityCookiePolicy,
      timeProvider: TimeProvider,
      properties: AtomicSecurityProperties,
  ): JwtSecurityConfigurerAdapter {
    val jwtProvider =
        jwtProviderProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.security.enabled=true requires JwtProvider for JwtSecurityConfigurerAdapter. " +
                        "Set atomic.security.jwt.access-key/refresh-key or register a custom JwtProvider bean.",
                )
                .also {
                  log.error(
                      "Security auto-configuration fail-fast: JwtProvider is missing while JwtSecurityConfigurerAdapter is required.",
                  )
                }
    return JwtSecurityConfigurerAdapter(
        jwtProvider = jwtProvider,
        objectMapper = objectMapper,
        excludeUrls = properties.excludeUrls.toList(),
        clientChannelResolver = clientChannelResolver,
        cookiePolicy = cookiePolicy,
        timeProvider = timeProvider,
    )
  }

  private fun normalizePreviousKeys(
      raw: Map<String, String>,
      activeKeyId: String,
      propertyName: String,
  ): Map<String, String> {
    val normalized = linkedMapOf<String, String>()
    raw.forEach { (rawKeyId, rawSecret) ->
      val keyId = rawKeyId.trim()
      val secret = rawSecret.trim()
      require(keyId.isNotBlank()) { "$propertyName contains a blank key id." }
      require(secret.isNotBlank()) { "$propertyName[$keyId] must not be blank." }
      require(keyId != activeKeyId) {
        "$propertyName must not reuse the active key id `$activeKeyId`."
      }
      require(normalized.putIfAbsent(keyId, secret) == null) {
        "$propertyName contains duplicate key id `$keyId`."
      }
    }
    return normalized
  }
}
