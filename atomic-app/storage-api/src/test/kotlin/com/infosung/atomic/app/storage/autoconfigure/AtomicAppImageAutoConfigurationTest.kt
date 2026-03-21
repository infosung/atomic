package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageController
import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageHttpExceptionHandler
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.InspectDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.RecoverDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory

class AtomicAppImageAutoConfigurationTest {
  private val webAutoConfiguration = AtomicAppImageWebAutoConfiguration()
  private val coreAutoConfiguration = AtomicAppImageCoreAutoConfiguration()
  private val persistenceAutoConfiguration = AtomicAppImagePersistenceAutoConfiguration()

  @Test
  fun `missing upload use case should fail fast when image api is enabled`() {
    val exception =
        assertFailsWith<IllegalStateException> {
          webAutoConfiguration.appStorageController(
              uploadAppImageUseCaseProvider = provider(),
              deleteAppImageUseCaseProvider =
                  provider(
                      DeleteAppImageUseCase::class.java, mock(DeleteAppImageUseCase::class.java)),
              properties = AtomicAppImageProperties().apply { enabled = true },
          )
        }

    assertTrue(
        exception.message!!.contains(
            "atomic.app.image.enabled=true requires UploadAppImageUseCase"),
    )
  }

  @Test
  fun `app storage controller should be created when image use cases are present`() {
    val controller =
        webAutoConfiguration.appStorageController(
            uploadAppImageUseCaseProvider =
                provider(
                    UploadAppImageUseCase::class.java, mock(UploadAppImageUseCase::class.java)),
            deleteAppImageUseCaseProvider =
                provider(
                    DeleteAppImageUseCase::class.java, mock(DeleteAppImageUseCase::class.java)),
            properties = AtomicAppImageProperties().apply { enabled = true },
        )

    assertIs<AppStorageController>(controller)
  }

  @Test
  fun `app storage http exception handler should be created`() {
    assertIs<AppStorageHttpExceptionHandler>(webAutoConfiguration.appStorageHttpExceptionHandler())
  }

  @Test
  fun `core auto configuration should expose storage use case seams without concrete bean seam`() {
    val imageMetadataPort =
        mock(com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort::class.java)
    val imageObjectStoragePort =
        mock(
            com.infosung.atomic.app.storage.application.port.out.ImageObjectStoragePort::class.java)
    val operations =
        coreAutoConfiguration.appImageApiOperations(
            imageMetadataPort = imageMetadataPort,
            imageObjectStoragePort = imageObjectStoragePort,
            properties = AtomicAppImageProperties(),
        )
    val uploadUseCase =
        coreAutoConfiguration.uploadAppImageUseCase(appImageApiOperations = operations)
    val deleteUseCase =
        coreAutoConfiguration.deleteAppImageUseCase(appImageApiOperations = operations)

    assertIs<UploadAppImageUseCase>(uploadUseCase)
    assertIs<DeleteAppImageUseCase>(deleteUseCase)
  }

  @Test
  fun `recovery use case seams should be created without concrete bean seam`() {
    val operations =
        coreAutoConfiguration.appImageDeleteRecoveryOperations(
            imageMetadataPort =
                mock(
                    com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort::class
                        .java),
            imageObjectStoragePort =
                mock(
                    com.infosung.atomic.app.storage.application.port.out
                            .ImageObjectStoragePort::class
                        .java),
            clockProvider = provider(Clock::class.java, Clock.systemUTC()),
        )
    val inspectUseCase =
        coreAutoConfiguration.inspectDeletePendingImagesUseCase(
            appImageDeleteRecoveryOperations = operations)
    val recoverUseCase =
        coreAutoConfiguration.recoverDeletePendingImagesUseCase(
            appImageDeleteRecoveryOperations = operations)

    assertIs<InspectDeletePendingImagesUseCase>(inspectUseCase)
    assertIs<RecoverDeletePendingImagesUseCase>(recoverUseCase)
  }

  @Test
  fun `persistence auto configuration should create preflight`() {
    assertIs<AppImageSchemaUpgradePreflight>(
        persistenceAutoConfiguration.appImageSchemaUpgradePreflight(
            mock(org.springframework.jdbc.core.JdbcTemplate::class.java),
        ),
    )
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
