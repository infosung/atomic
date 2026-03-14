package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.AppVersionController
import com.infosung.atomic.app.version.AppVersionHttpExceptionHandler
import com.infosung.atomic.app.version.ServiceVersionRepository
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class AtomicAppVersionAutoConfigurationContractTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtomicAppVersionAutoConfiguration::class.java))

  @Test
  fun `disabled version api should not register controller or service beans`() {
    contextRunner.run { context ->
      assertTrue(context.getBeansOfType(AppVersionCheckService::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppVersionController::class.java).isEmpty())
      assertTrue(context.getBeansOfType(AppVersionHttpExceptionHandler::class.java).isEmpty())
    }
  }

  @Test
  fun `auto configuration factory methods should create service and controller when dependencies exist`() {
    val autoConfiguration = AtomicAppVersionAutoConfiguration()
    val repository = mock(ServiceVersionRepository::class.java)
    val properties =
        AtomicAppVersionProperties().apply {
          enabled = true
          defaultStoreUrl = "https://env.example.com/store"
        }

    val service = autoConfiguration.appVersionCheckService(repository, properties)
    val controller = autoConfiguration.appVersionController(service)
    val handler = autoConfiguration.appVersionHttpExceptionHandler()

    assertIs<AppVersionCheckService>(service)
    assertIs<AppVersionController>(controller)
    assertIs<AppVersionHttpExceptionHandler>(handler)
  }
}
