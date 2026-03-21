package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageController
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.InspectDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.RecoverDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.application.service.AppImageApiService
import com.infosung.atomic.app.storage.application.service.AppImageDeleteRecoveryService
import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.ImageService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class AtomicAppImageAutoConfigurationContextTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AtomicAppImageCoreAutoConfiguration::class.java,
                  AtomicAppImageWebAutoConfiguration::class.java,
              ),
          )
          .withUserConfiguration(TestConfiguration::class.java)

  @Test
  fun `split core and web auto configuration should expose use case seams without concrete service beans`() {
    contextRunner.run { context ->
      assertNotNull(context.getBean(AppStorageController::class.java))
      assertNotNull(context.getBean(UploadAppImageUseCase::class.java))
      assertNotNull(context.getBean(DeleteAppImageUseCase::class.java))
      assertNotNull(context.getBean(InspectDeletePendingImagesUseCase::class.java))
      assertNotNull(context.getBean(RecoverDeletePendingImagesUseCase::class.java))
      assertEquals(0, context.getBeanNamesForType(AppImageApiService::class.java).size)
      assertEquals(0, context.getBeanNamesForType(AppImageDeleteRecoveryService::class.java).size)
    }
  }

  @Configuration(proxyBeanMethods = false)
  class TestConfiguration {
    @Bean("storageClients")
    fun storageClients(): Map<String, StorageClient> = mapOf("svc:s3" to NoopStorageClient())

    @Bean
    fun storageProfiles(): Map<String, StorageProfile> =
        mapOf(
            "svc:s3" to
                StorageProfile(
                    bucket = "bucket",
                    cdn = "https://cdn.example.com",
                ),
        )

    @Bean
    fun imageService(
        storageClients: Map<String, StorageClient>,
        storageProfiles: Map<String, StorageProfile>,
    ): ImageService =
        ImageService(storageClients = storageClients, storageProfiles = storageProfiles)

    @Bean
    fun imageMetadataPort(): ImageMetadataPort =
        object : ImageMetadataPort {
          override fun findByIdOrThrow(
              imageId: java.util.UUID,
              rawImageId: String,
          ) = throw UnsupportedOperationException()

          override fun save(image: com.infosung.atomic.app.storage.domain.StoredImage) = image

          override fun markDeletePending(
              image: com.infosung.atomic.app.storage.domain.StoredImage
          ) = image

          override fun purgeDeletePending(
              image: com.infosung.atomic.app.storage.domain.StoredImage,
          ) = Unit

          override fun inspectDeletePendingImages() =
              com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot(0, null)

          override fun claimDeletePending(
              limit: Int,
              claimToken: String,
              claimedAt: java.time.LocalDateTime,
          ): List<com.infosung.atomic.app.storage.domain.StoredImage> = emptyList()

          override fun releaseDeleteRecoveryClaim(
              imageId: java.util.UUID,
              claimToken: String,
          ) = Unit
        }

    @Bean
    fun atomicAppImageProperties(): AtomicAppImageProperties =
        AtomicAppImageProperties().apply {
          enabled = true
          endpointPath = "/api/v1/storage/image"
        }
  }

  private class NoopStorageClient : StorageClient {
    override fun putObject(request: PutObjectRequest) = Unit

    override fun deleteObject(objectKey: String) = Unit
  }
}
