package com.infosung.atomic.app.version.autoconfigure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

class AtomicAppVersionPropertiesEnvironmentContractTest {
  @Test
  fun `default values should stay aligned with documented usage`() {
    val properties = bindEnvironmentVariables(emptyMap())

    assertFalse(properties.enabled)
    assertEquals("https://www.infosung.com", properties.defaultStoreUrl)
    assertEquals("/api/v1/version/check", properties.endpointPath)
  }

  @Test
  fun `environment variable names should bind to version properties`() {
    val properties =
        bindEnvironmentVariables(
            mapOf(
                "ATOMIC_APP_VERSION_ENABLED" to "true",
                "ATOMIC_APP_VERSION_DEFAULT_STORE_URL" to "https://env.example.com/store",
                "ATOMIC_APP_VERSION_ENDPOINT_PATH" to "/env/version/check",
            ),
        )

    assertTrue(properties.enabled)
    assertEquals("https://env.example.com/store", properties.defaultStoreUrl)
    assertEquals("/env/version/check", properties.endpointPath)
  }

  private fun bindEnvironmentVariables(
      variables: Map<String, Any>,
  ): AtomicAppVersionProperties {
    val environment = StandardEnvironment()
    environment.propertySources.addFirst(SystemEnvironmentPropertySource("test-env", variables))
    ConfigurationPropertySources.attach(environment)
    return Binder.get(environment)
        .bind("atomic.app.version", Bindable.of(AtomicAppVersionProperties::class.java))
        .orElseGet { AtomicAppVersionProperties() }
  }
}
