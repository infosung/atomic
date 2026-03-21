package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionHttpExceptionHandler
import com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.application.service.CheckAppVersionService
import com.infosung.atomic.app.version.domain.VersionCheckDecision
import java.lang.reflect.Method
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.FilteredClassLoader
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
      assertTrue(context.getBeansOfType(CheckAppVersionUseCase::class.java).isEmpty())
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
  fun `split auto configuration factory methods should create port use-case and web beans when dependencies exist`() {
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
    val controller = webAutoConfiguration.appVersionController(useCase)
    val handler = webAutoConfiguration.appVersionHttpExceptionHandler()

    assertIs<LoadVersionPolicyPort>(loadPort)
    assertIs<JpaLoadVersionPolicyAdapter>(loadPort)
    assertIs<CheckAppVersionUseCase>(useCase)
    assertIs<CheckAppVersionService>(useCase)
    assertIs<AppVersionController>(controller)
    assertIs<AppVersionHttpExceptionHandler>(handler)
  }

  @Test
  fun `umbrella auto configuration should wire custom use-case into controller path in a real context`() {
    val customUseCase = RecordingCheckAppVersionUseCase()

    ApplicationContextRunner()
        .withInitializer { context ->
          context.setClassLoader(
              FilteredClassLoader(
                  "org.springframework.data.jpa.repository.JpaRepository",
                  "jakarta.persistence.Entity",
              ),
          )
        }
        .withConfiguration(AutoConfigurations.of(AtomicAppVersionAutoConfiguration::class.java))
        .withPropertyValues(
            "atomic.app.version.enabled=true",
            "atomic.app.version.default-store-url=https://env.example.com/store",
        )
        .withBean(CheckAppVersionUseCase::class.java, Supplier { customUseCase })
        .run { context ->
          assertSame(customUseCase, context.getBean(CheckAppVersionUseCase::class.java))
          val controller = context.getBean(AppVersionController::class.java)
          assertTrue(context.containsBean("appVersionHttpExceptionHandler"))

          val response = controller.getVersion("MY_SERVICE", "ANDROID", "1.2.3")

          assertEquals("MY_SERVICE", customUseCase.lastService)
          assertEquals("ANDROID", customUseCase.lastPlatform)
          assertEquals("1.2.3", customUseCase.lastAppVersion)
          assertEquals("9.9.9", response.data?.currentVersion)
        }
  }

  @Test
  fun `auto configuration factory method should create schema upgrade preflight`() {
    val autoConfiguration = AtomicAppVersionPersistenceAutoConfiguration()

    val preflight =
        autoConfiguration.appVersionSchemaUpgradePreflight(mock(JdbcTemplate::class.java))

    assertIs<AppVersionSchemaUpgradePreflight>(preflight)
  }

  @Test
  fun `auto configuration should keep app version use-case override guard on exported bean`() {
    val beanMethod =
        AtomicAppVersionCoreAutoConfiguration::class
            .java
            .declaredMethods
            .single(::isCheckAppVersionUseCaseBeanMethod)

    assertTrue(beanMethod.isAnnotationPresent(ConditionalOnMissingBean::class.java))
    assertEquals(CheckAppVersionUseCase::class.java, beanMethod.returnType)
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

  private fun isCheckAppVersionUseCaseBeanMethod(method: Method): Boolean {
    return method.name.substringBefore('$') == "checkAppVersionUseCase" &&
        method.returnType == CheckAppVersionUseCase::class.java &&
        method.parameterTypes.contentEquals(
            arrayOf(
                LoadVersionPolicyPort::class.java,
                AtomicAppVersionProperties::class.java,
            ),
        )
  }

  private fun isAppVersionControllerBeanMethod(method: Method): Boolean {
    return method.name == "appVersionController" &&
        method.returnType == AppVersionController::class.java &&
        method.parameterTypes.contentEquals(arrayOf(CheckAppVersionUseCase::class.java))
  }

  private fun isAppVersionHttpExceptionHandlerBeanMethod(method: Method): Boolean {
    return method.name == "appVersionHttpExceptionHandler" &&
        method.returnType == AppVersionHttpExceptionHandler::class.java &&
        method.parameterTypes.isEmpty()
  }

  private class RecordingCheckAppVersionUseCase : CheckAppVersionUseCase {
    var lastService: String? = null
    var lastPlatform: String? = null
    var lastAppVersion: String? = null

    override fun check(
        service: String,
        platform: String,
        appVersion: String,
    ): VersionCheckDecision {
      lastService = service
      lastPlatform = platform
      lastAppVersion = appVersion
      return VersionCheckDecision(
          currentVersion = "9.9.9",
          userVersion = appVersion,
          requiredUpdate = false,
          storeUrl = "https://override.example.com/store",
      )
    }
  }
}
