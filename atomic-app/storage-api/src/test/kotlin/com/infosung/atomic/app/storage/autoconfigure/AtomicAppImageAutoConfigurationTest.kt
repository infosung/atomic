package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.AppImageApiService
import com.infosung.atomic.app.storage.AppStorageController
import com.infosung.atomic.app.storage.AppStorageHttpExceptionHandler
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory

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
