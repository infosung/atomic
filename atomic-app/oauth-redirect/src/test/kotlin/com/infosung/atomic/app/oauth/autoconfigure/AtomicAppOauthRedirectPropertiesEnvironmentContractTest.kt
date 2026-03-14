package com.infosung.atomic.app.oauth.autoconfigure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.SystemEnvironmentPropertySource

class AtomicAppOauthRedirectPropertiesEnvironmentContractTest {
  private val contextRunner =
      ApplicationContextRunner().withUserConfiguration(PropertiesBindingConfiguration::class.java)

  @Test
  fun `default values should stay aligned with documented usage`() {
    withEnvironmentVariables(emptyMap()).run { context ->
      val properties = context.getBean(AtomicAppOauthRedirectProperties::class.java)

      assertFalse(properties.enabled)
      assertEquals("/oauth/redirect", properties.redirectEndpointPath)
      assertEquals("/oauth/callback", properties.callbackEndpointPath)
      assertEquals("relayCode", properties.relayCodeQueryParameterName)
      assertEquals(300, properties.relayCodeTtlSeconds)
      assertTrue(properties.callbackBinding.enabled)
      assertNull(properties.callbackBinding.mode)
      assertEquals(
          AtomicAppOauthRedirectProperties.CallbackBindingMode.STRICT,
          properties.callbackBinding.resolvedMode(),
      )
      assertEquals("atomicCallbackBinding", properties.callbackBinding.stateAttributeKey)
      assertEquals("__Host-atomic_oauth_callback_binding", properties.callbackBinding.cookieName)
      assertEquals("None", properties.callbackBinding.cookieSameSite)
      assertEquals("/", properties.callbackBinding.cookiePath)
      assertTrue(properties.callbackBinding.cookieSecure)
      assertEquals(600, properties.callbackBinding.cookieMaxAgeSeconds)
      assertEquals(AtomicAppOauthRedirectProperties.StoreType.ENTITY, properties.store.type)
      assertTrue(properties.store.failFast)
      assertEquals("atomicOauthRelayCode", properties.store.cache.cacheName)
      assertEquals("atomic:oauth:relay:", properties.store.cache.keyPrefix)
      assertEquals("atomic_oauth_relay_code", properties.store.entity.tableName)
    }
  }

  @Test
  fun `environment variable names should bind nested oauth redirect properties`() {
    withEnvironmentVariables(
            mapOf(
                "ATOMIC_APP_OAUTH_REDIRECT_ENABLED" to "true",
                "ATOMIC_APP_OAUTH_REDIRECT_REDIRECT_ENDPOINT_PATH" to "/env/oauth/redirect",
                "ATOMIC_APP_OAUTH_REDIRECT_CALLBACK_ENDPOINT_PATH" to "/env/oauth/callback",
                "ATOMIC_APP_OAUTH_REDIRECT_RELAY_CODE_QUERY_PARAMETER_NAME" to "code",
                "ATOMIC_APP_OAUTH_REDIRECT_RELAY_CODE_TTL_SECONDS" to "900",
                "ATOMIC_APP_OAUTH_REDIRECT_ALLOWED_REDIRECT_URI_PREFIXES" to
                    "https://app.example.com/oauth",
            ),
        )
        .run { context ->
          val properties = context.getBean(AtomicAppOauthRedirectProperties::class.java)

          assertTrue(properties.enabled)
          assertEquals("/env/oauth/redirect", properties.redirectEndpointPath)
          assertEquals("/env/oauth/callback", properties.callbackEndpointPath)
          assertEquals("code", properties.relayCodeQueryParameterName)
          assertEquals(900, properties.relayCodeTtlSeconds)
          assertEquals(listOf("https://app.example.com/oauth"), properties.allowedRedirectUriPrefixes)
        }
  }

  @Test
  fun `canonical property keys should bind callback binding and store properties`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.callback-binding.mode=relaxed",
            "atomic.app.oauth.redirect.callback-binding.cookie-name=__Host-env_oauth_binding",
            "atomic.app.oauth.redirect.store.type=cache",
            "atomic.app.oauth.redirect.store.fail-fast=false",
            "atomic.app.oauth.redirect.store.cache.cache-name=envOauthRelayCode",
            "atomic.app.oauth.redirect.store.cache.key-prefix=env:oauth:relay:",
            "atomic.app.oauth.redirect.store.cache.ttl-seconds=120",
        )
        .run { context ->
          val properties = context.getBean(AtomicAppOauthRedirectProperties::class.java)

          assertTrue(properties.callbackBinding.enabled)
          assertEquals(
              AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED,
              properties.callbackBinding.mode,
          )
          assertEquals(
              AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED,
              properties.callbackBinding.resolvedMode(),
          )
          assertEquals("__Host-env_oauth_binding", properties.callbackBinding.cookieName)
          assertEquals(AtomicAppOauthRedirectProperties.StoreType.CACHE, properties.store.type)
          assertFalse(properties.store.failFast)
          assertEquals("envOauthRelayCode", properties.store.cache.cacheName)
          assertEquals("env:oauth:relay:", properties.store.cache.keyPrefix)
          assertEquals(120, properties.store.cache.ttlSeconds)
        }
  }

  @Test
  fun `callback binding mode should override legacy enabled flag when explicitly configured`() {
    contextRunner
        .withPropertyValues(
            "atomic.app.oauth.redirect.callback-binding.enabled=false",
            "atomic.app.oauth.redirect.callback-binding.mode=relaxed",
        )
        .run { context ->
          val properties = context.getBean(AtomicAppOauthRedirectProperties::class.java)

          assertFalse(properties.callbackBinding.enabled)
          assertEquals(
              AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED,
              properties.callbackBinding.mode,
          )
          assertEquals(
              AtomicAppOauthRedirectProperties.CallbackBindingMode.RELAXED,
              properties.callbackBinding.resolvedMode(),
          )
          assertTrue(properties.callbackBinding.isCookieValidationEnabled())
        }
  }

  private fun withEnvironmentVariables(
      variables: Map<String, Any>,
  ): ApplicationContextRunner {
    return contextRunner.withInitializer { context ->
      context.environment.propertySources.addFirst(
          SystemEnvironmentPropertySource("test-env", variables),
      )
    }
  }

  @Configuration
  @EnableConfigurationProperties(AtomicAppOauthRedirectProperties::class)
  class PropertiesBindingConfiguration
}
