package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.AppImageApiService
import com.infosung.atomic.app.storage.AppImageDeleteRecoveryService
import com.infosung.atomic.app.storage.AppStorageController
import com.infosung.atomic.app.storage.AppStorageHttpExceptionHandler
import com.infosung.atomic.storage.image.ImageService
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.jdbc.core.JdbcTemplate

class AtomicAppImageAutoConfigurationTest {
  private val autoConfiguration = AtomicAppImageAutoConfiguration()

  @Test
  fun `missing app image api service should fail fast when image api is enabled`() {
    val exception =
        assertFailsWith<IllegalStateException> {
          autoConfiguration.appStorageController(
              appImageApiServiceProvider = provider(),
              properties = AtomicAppImageProperties().apply { enabled = true },
          )
        }

    assertNotNull(exception.message)
    assertTrue(
        exception.message!!.contains("atomic.app.image.enabled=true requires AppImageApiService"))
  }

  @Test
  fun `app storage controller should be created when app image api service is present`() {
    val controller =
        autoConfiguration.appStorageController(
            appImageApiServiceProvider =
                provider(AppImageApiService::class.java, mock(AppImageApiService::class.java)),
            properties = AtomicAppImageProperties().apply { enabled = true },
        )

    assertIs<AppStorageController>(controller)
  }

  @Test
  fun `app storage http exception handler should be created`() {
    val handler = autoConfiguration.appStorageHttpExceptionHandler()

    assertIs<AppStorageHttpExceptionHandler>(handler)
  }

  @Test
  fun `app image delete recovery service should be created`() {
    val recoveryService =
        autoConfiguration.appImageDeleteRecoveryService(
            appImageEntityTxService =
                mock(com.infosung.atomic.app.storage.AppImageEntityTxService::class.java),
            imageService = mock(ImageService::class.java),
            clockProvider = provider(Clock::class.java, Clock.systemUTC()),
        )

    assertIs<AppImageDeleteRecoveryService>(recoveryService)
  }

  @Test
  fun `app image schema upgrade preflight should be created`() {
    val preflight = autoConfiguration.appImageSchemaUpgradePreflight(mock(JdbcTemplate::class.java))

    assertIs<AppImageSchemaUpgradePreflight>(preflight)
  }

  private fun <T : Any> provider(
      type: Class<T>,
      bean: T? = null,
  ): ObjectProvider<T> {
    val beanFactory = DefaultListableBeanFactory()
    if (bean != null) {
      beanFactory.registerSingleton(type.name, bean)
    }
    return beanFactory.getBeanProvider(type)
  }

  private inline fun <reified T : Any> provider(): ObjectProvider<T> {
    return provider(T::class.java)
  }
}
