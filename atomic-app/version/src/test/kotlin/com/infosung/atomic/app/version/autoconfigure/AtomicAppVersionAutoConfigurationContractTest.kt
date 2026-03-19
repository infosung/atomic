package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.AppVersionController
import com.infosung.atomic.app.version.AppVersionHttpExceptionHandler
import com.infosung.atomic.app.version.ServiceVersionRepository
import com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.application.service.CheckAppVersionService
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

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
  fun `umbrella auto configuration should import web core and persistence layer configs`() {
    val imported =
        AtomicAppVersionAutoConfiguration::class
            .java
            .getAnnotation(Import::class.java)
            .value
            .toSet()

    assertEquals(
        setOf(
            AtomicAppVersionCoreAutoConfiguration::class,
            AtomicAppVersionPersistenceAutoConfiguration::class,
            AtomicAppVersionWebAutoConfiguration::class,
        ),
        imported,
    )
  }

  @Test
  fun `split auto configuration factory methods should create port use-case facade and web beans when dependencies exist`() {
    val coreAutoConfiguration = AtomicAppVersionCoreAutoConfiguration()
    val persistenceAutoConfiguration = AtomicAppVersionPersistenceAutoConfiguration()
    val webAutoConfiguration = AtomicAppVersionWebAutoConfiguration()
    val repository = mock(ServiceVersionRepository::class.java)
    val properties =
        AtomicAppVersionProperties().apply {
          enabled = true
          defaultStoreUrl = "https://env.example.com/store"
        }

    val loadPort = persistenceAutoConfiguration.loadVersionPolicyPort(repository)
    val useCase = coreAutoConfiguration.checkAppVersionUseCase(loadPort, properties)
    val service = coreAutoConfiguration.appVersionCheckService(properties, useCase)
    val controller = webAutoConfiguration.appVersionController(service)
    val handler = webAutoConfiguration.appVersionHttpExceptionHandler()

    assertIs<LoadVersionPolicyPort>(loadPort)
    assertIs<JpaLoadVersionPolicyAdapter>(loadPort)
    assertIs<CheckAppVersionUseCase>(useCase)
    assertIs<CheckAppVersionService>(useCase)
    assertIs<AppVersionCheckService>(service)
    assertIs<AppVersionController>(controller)
    assertIs<AppVersionHttpExceptionHandler>(handler)
  }

  @Test
  fun `auto configuration factory method should create schema upgrade preflight`() {
    val autoConfiguration = AtomicAppVersionPersistenceAutoConfiguration()

    val preflight =
        autoConfiguration.appVersionSchemaUpgradePreflight(mock(JdbcTemplate::class.java))

    assertIs<AppVersionSchemaUpgradePreflight>(preflight)
  }

  @Test
  fun `auto configuration should keep app version service override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionCoreAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isAppVersionServiceBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppVersionCheckService::class.java, beanMethod.returnType)
  }

  @Test
  fun `auto configuration should keep app version controller override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionWebAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isAppVersionControllerBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppVersionController::class.java, beanMethod.returnType)
  }

  @Test
  fun `auto configuration should keep app version exception handler override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionWebAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isAppVersionHttpExceptionHandlerBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppVersionHttpExceptionHandler::class.java, beanMethod.returnType)
  }

  private fun isAppVersionServiceBeanMethod(method: Method): Boolean {
    return method.name.startsWith("appVersionCheckService") &&
        method.returnType == AppVersionCheckService::class.java &&
        method.parameterTypes.contentEquals(
            arrayOf(
                AtomicAppVersionProperties::class.java,
                CheckAppVersionUseCase::class.java,
            ),
        )
  }

  private fun isAppVersionControllerBeanMethod(method: Method): Boolean {
    return method.name == "appVersionController" &&
        method.returnType == AppVersionController::class.java &&
        method.parameterTypes.contentEquals(arrayOf(AppVersionCheckService::class.java))
  }

  private fun isAppVersionHttpExceptionHandlerBeanMethod(method: Method): Boolean {
    return method.name == "appVersionHttpExceptionHandler" &&
        method.returnType == AppVersionHttpExceptionHandler::class.java &&
        method.parameterTypes.isEmpty()
  }
}
