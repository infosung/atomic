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
  fun `auto configuration factory methods should create port use-case and facade when dependencies exist`() {
    val autoConfiguration = AtomicAppVersionAutoConfiguration()
    val repository = mock(ServiceVersionRepository::class.java)
    val properties =
        AtomicAppVersionProperties().apply {
          enabled = true
          defaultStoreUrl = "https://env.example.com/store"
        }

    val loadPort = autoConfiguration.loadVersionPolicyPort(repository)
    val useCase = autoConfiguration.checkAppVersionUseCase(loadPort, properties)
    val service = autoConfiguration.appVersionCheckService(repository, properties, useCase)
    val controller = autoConfiguration.appVersionController(service)
    val handler = autoConfiguration.appVersionHttpExceptionHandler()

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
    val autoConfiguration = AtomicAppVersionAutoConfiguration()

    val preflight =
        autoConfiguration.appVersionSchemaUpgradePreflight(mock(JdbcTemplate::class.java))

    assertIs<AppVersionSchemaUpgradePreflight>(preflight)
  }

  @Test
  fun `auto configuration should keep app version service override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isAppVersionServiceBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppVersionCheckService::class.java, beanMethod.returnType)
  }

  @Test
  fun `auto configuration should keep app version controller override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isAppVersionControllerBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(AppVersionController::class.java, beanMethod.returnType)
  }

  @Test
  fun `auto configuration should keep app version exception handler override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionAutoConfiguration::class
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
                ServiceVersionRepository::class.java,
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
