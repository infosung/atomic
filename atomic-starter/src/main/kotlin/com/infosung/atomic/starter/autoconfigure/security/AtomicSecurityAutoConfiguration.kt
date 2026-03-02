package com.infosung.atomic.starter.autoconfigure.security

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.channel.DefaultClientChannelResolver
import com.infosung.atomic.spring.security.config.JwtSecurityConfigurerAdapter
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
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

    return JwtProvider(
        accessKey = accessKey,
        refreshKey = refreshKey,
        algorithm = properties.jwt.algorithm,
        serviceName = properties.jwt.serviceName,
        accessExpiredSecond = properties.jwt.accessExpiredSecond,
        refreshExpiredSecond = properties.jwt.refreshExpiredSecond,
        timeProvider = timeProvider,
    )
  }

  /** Registers [JwtSecurityConfigurerAdapter] for explicit HttpSecurity integration. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ObjectMapper::class)
  @ConditionalOnClass(name = ["tools.jackson.databind.ObjectMapper"])
  fun jwtSecurityConfigurerAdapter(
      jwtProvider: JwtProvider,
      objectMapper: ObjectMapper,
      clientChannelResolver: ClientChannelResolver,
      cookiePolicy: SecurityCookiePolicy,
      timeProvider: TimeProvider,
      properties: AtomicSecurityProperties,
  ): JwtSecurityConfigurerAdapter {
    return JwtSecurityConfigurerAdapter(
        jwtProvider = jwtProvider,
        objectMapper = objectMapper,
        excludeUrls = properties.excludeUrls.toList(),
        clientChannelResolver = clientChannelResolver,
        cookiePolicy = cookiePolicy,
        timeProvider = timeProvider,
    )
  }
}
