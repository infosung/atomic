package com.infosung.atomic.app.storage.autoconfigure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

class AtomicAppImagePropertiesEnvironmentContractTest {
  @Test
  fun `default values should stay aligned with documented usage`() {
    val properties = bindEnvironmentVariables(emptyMap())

    assertFalse(properties.enabled)
    assertEquals("/api/v1/storage/image", properties.endpointPath)
    assertEquals(1.0, properties.defaultQuality)
    assertEquals(0.1, properties.minQuality)
    assertEquals(1.0, properties.maxQuality)
    assertFalse(properties.uploaderParameterEnabled)
    assertEquals("uploaderId", properties.uploaderParameterName)
  }

  @Test
  fun `environment variable names should bind image api properties`() {
    val properties =
        bindEnvironmentVariables(
            mapOf(
                "ATOMIC_APP_IMAGE_ENABLED" to "true",
                "ATOMIC_APP_IMAGE_ENDPOINT_PATH" to "/env/storage/image",
                "ATOMIC_APP_IMAGE_DEFAULT_QUALITY" to "0.85",
                "ATOMIC_APP_IMAGE_MIN_QUALITY" to "0.25",
                "ATOMIC_APP_IMAGE_MAX_QUALITY" to "0.95",
                "ATOMIC_APP_IMAGE_UPLOADER_PARAMETER_ENABLED" to "true",
                "ATOMIC_APP_IMAGE_UPLOADER_PARAMETER_NAME" to "memberId",
            ),
        )

    assertTrue(properties.enabled)
    assertEquals("/env/storage/image", properties.endpointPath)
    assertEquals(0.85, properties.defaultQuality)
    assertEquals(0.25, properties.minQuality)
    assertEquals(0.95, properties.maxQuality)
    assertTrue(properties.uploaderParameterEnabled)
    assertEquals("memberId", properties.uploaderParameterName)
  }

  private fun bindEnvironmentVariables(
      variables: Map<String, Any>,
  ): AtomicAppImageProperties {
    val environment = StandardEnvironment()
    environment.propertySources.addFirst(SystemEnvironmentPropertySource("test-env", variables))
    ConfigurationPropertySources.attach(environment)
    return Binder.get(environment)
        .bind("atomic.app.image", Bindable.of(AtomicAppImageProperties::class.java))
        .orElseGet { AtomicAppImageProperties() }
  }
}
