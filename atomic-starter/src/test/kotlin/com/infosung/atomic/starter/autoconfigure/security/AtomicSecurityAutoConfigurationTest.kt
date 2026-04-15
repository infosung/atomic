package com.infosung.atomic.starter.autoconfigure.security

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.config.JwtSecurityConfigurerAdapter
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.nimbusds.jwt.SignedJWT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

class AtomicSecurityAutoConfigurationTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtomicSecurityAutoConfiguration::class.java))
  private val currentAccessKey = "current-access-key".repeat(5)
  private val currentRefreshKey = "current-refresh-key".repeat(5)
  private val previousAccessKey = "previous-access-key".repeat(5)
  private val previousRefreshKey = "previous-refresh-key".repeat(5)

  @Test
  fun `missing jwt keys should fail fast when security is enabled`() {
    contextRunner
        .withUserConfiguration(CommonSecurityPrerequisites::class.java)
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=true",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains("requires JwtProvider") == true ||
                  failure.message?.contains("No qualifying bean") == true,
          )
        }
  }

  @Test
  fun `jwt enabled false without custom jwt provider should still fail fast`() {
    contextRunner
        .withUserConfiguration(CommonSecurityPrerequisites::class.java)
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=false",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(
              failure.message?.contains("requires JwtProvider") == true ||
                  failure.message?.contains("No qualifying bean") == true,
          )
        }
  }

  @Test
  fun `configured jwt keys should register provider and configurer`() {
    contextRunner
        .withUserConfiguration(CommonSecurityPrerequisites::class.java)
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=true",
            "atomic.security.jwt.access-key=test-access-key",
            "atomic.security.jwt.refresh-key=test-refresh-key",
        )
        .run { context ->
          assertEquals(null, context.startupFailure)
          assertIs<JwtProvider>(context.getBean(JwtProvider::class.java))
          assertIs<JwtSecurityConfigurerAdapter>(
              context.getBean(JwtSecurityConfigurerAdapter::class.java))
        }
  }

  @Test
  fun `configured previous jwt keys should participate in rotation`() {
    contextRunner
        .withUserConfiguration(CommonSecurityPrerequisites::class.java)
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=true",
            "atomic.security.jwt.access-key=$currentAccessKey",
            "atomic.security.jwt.refresh-key=$currentRefreshKey",
            "atomic.security.jwt.access-key-id=access-v2",
            "atomic.security.jwt.refresh-key-id=refresh-v2",
            "atomic.security.jwt.previous-access-keys.access-v1=$previousAccessKey",
            "atomic.security.jwt.previous-refresh-keys.refresh-v1=$previousRefreshKey",
        )
        .run { context ->
          assertEquals(null, context.startupFailure)
          val provider = context.getBean(JwtProvider::class.java)
          val issued = provider.createJwtDto(id = "123", subject = "USER")
          assertEquals("access-v2", SignedJWT.parse(issued.accessToken).header.keyID)
          assertEquals("refresh-v2", SignedJWT.parse(issued.refreshToken).header.keyID)
        }
  }

  @Test
  fun `duplicate active and previous key ids should fail fast`() {
    contextRunner
        .withUserConfiguration(CommonSecurityPrerequisites::class.java)
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=true",
            "atomic.security.jwt.access-key=$currentAccessKey",
            "atomic.security.jwt.refresh-key=$currentRefreshKey",
            "atomic.security.jwt.access-key-id=access-v2",
            "atomic.security.jwt.previous-access-keys.access-v2=$previousAccessKey",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertTrue(failure.message?.contains("previous-access-keys") == true)
        }
  }

  @Test
  fun `custom jwt provider should satisfy configurer even when jwt auto provider is disabled`() {
    contextRunner
        .withUserConfiguration(
            CommonSecurityPrerequisites::class.java,
            CustomJwtProviderConfiguration::class.java,
        )
        .withPropertyValues(
            "atomic.security.enabled=true",
            "atomic.security.jwt.enabled=false",
        )
        .run { context ->
          assertEquals(null, context.startupFailure)
          assertIs<JwtProvider>(context.getBean(JwtProvider::class.java))
          assertIs<JwtSecurityConfigurerAdapter>(
              context.getBean(JwtSecurityConfigurerAdapter::class.java))
        }
  }

  @Configuration
  class CommonSecurityPrerequisites {
    @Bean fun objectMapper(): ObjectMapper = ObjectMapper()

    @Bean fun timeProvider(): TimeProvider = TimeProvider()
  }

  @Configuration
  class CustomJwtProviderConfiguration {
    @Bean
    fun customJwtProvider(timeProvider: TimeProvider): JwtProvider {
      return JwtProvider(
          accessKey = "custom-access-key",
          refreshKey = "custom-refresh-key",
          serviceName = "test-service",
          accessExpiredSecond = 900,
          refreshExpiredSecond = 1209600,
          timeProvider = timeProvider,
      )
    }
  }
}
