package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.storage.ImageServiceStoragePortAdapter
import com.infosung.atomic.app.storage.application.model.AppImageRequestPolicy
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.InspectDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.RecoverDeletePendingImagesUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.application.port.out.ImageObjectStoragePort
import com.infosung.atomic.app.storage.application.service.AppImageApiService
import com.infosung.atomic.app.storage.application.service.AppImageDeleteRecoveryService
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.image.ImageService
import java.time.Clock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class AtomicAppImageCoreAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ImageService::class)
  fun imageObjectStoragePort(
      imageService: ImageService,
      @Qualifier("storageClients") storageClients: Map<String, StorageClient>,
  ): ImageObjectStoragePort {
    return ImageServiceStoragePortAdapter(
        imageService = imageService,
        storageClients = storageClients,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun imageMetadataPort(
      appImageEntityTxService: AppImageEntityTxService,
  ): ImageMetadataPort = appImageEntityTxService

  @Bean
  @ConditionalOnMissingBean
  internal fun appImageApiOperations(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      properties: AtomicAppImageProperties,
  ): AppImageApiOperations =
      AppImageApiOperations(
          AppImageApiService(
              imageMetadataPort = imageMetadataPort,
              imageObjectStoragePort = imageObjectStoragePort,
              requestPolicy = requestPolicy(properties),
          ),
      )

  @Bean
  @ConditionalOnMissingBean
  internal fun uploadAppImageUseCase(
      appImageApiOperations: AppImageApiOperations,
  ): UploadAppImageUseCase = UploadAppImageUseCase { command ->
    appImageApiOperations.upload(command)
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun deleteAppImageUseCase(
      appImageApiOperations: AppImageApiOperations,
  ): DeleteAppImageUseCase = DeleteAppImageUseCase { command ->
    appImageApiOperations.delete(command)
  }

  @Bean
  @ConditionalOnMissingBean
  internal fun appImageDeleteRecoveryOperations(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      clockProvider: ObjectProvider<Clock>,
  ): AppImageDeleteRecoveryOperations =
      AppImageDeleteRecoveryOperations(
          AppImageDeleteRecoveryService(
              imageMetadataPort = imageMetadataPort,
              imageObjectStoragePort = imageObjectStoragePort,
              clock = clockProvider.getIfAvailable { Clock.systemUTC() },
          ),
      )

  @Bean
  @ConditionalOnMissingBean
  internal fun inspectDeletePendingImagesUseCase(
      appImageDeleteRecoveryOperations: AppImageDeleteRecoveryOperations,
  ): InspectDeletePendingImagesUseCase =
      object : InspectDeletePendingImagesUseCase {
        override fun inspectDeletePendingImages() = appImageDeleteRecoveryOperations.inspect()
      }

  @Bean
  @ConditionalOnMissingBean
  internal fun recoverDeletePendingImagesUseCase(
      appImageDeleteRecoveryOperations: AppImageDeleteRecoveryOperations,
  ): RecoverDeletePendingImagesUseCase =
      object : RecoverDeletePendingImagesUseCase {
        override fun recoverDeletePendingImages(limit: Int) =
            appImageDeleteRecoveryOperations.recover(limit)
      }

  private fun requestPolicy(properties: AtomicAppImageProperties): AppImageRequestPolicy {
    return AppImageRequestPolicy(
        minQuality = properties.minQuality,
        maxQuality = properties.maxQuality,
        uploaderParameterEnabled = properties.uploaderParameterEnabled,
        uploaderParameterName = properties.uploaderParameterName,
    )
  }
}

internal class AppImageApiOperations(
    private val service: AppImageApiService,
) {
  fun upload(command: com.infosung.atomic.app.storage.application.model.UploadAppImageCommand) =
      service.uploadImage(command)

  fun delete(command: com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand) =
      service.deleteImage(command)
}

internal class AppImageDeleteRecoveryOperations(
    private val service: AppImageDeleteRecoveryService,
) {
  fun inspect() = service.inspectDeletePendingImages()

  fun recover(limit: Int) = service.recoverDeletePendingImages(limit)
}
