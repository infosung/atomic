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
  fun uploadAppImageUseCase(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      properties: AtomicAppImageProperties,
  ): UploadAppImageUseCase {
    val service =
        AppImageApiService(
            imageMetadataPort = imageMetadataPort,
            imageObjectStoragePort = imageObjectStoragePort,
            requestPolicy = requestPolicy(properties),
        )
    return UploadAppImageUseCase { command -> service.uploadImage(command) }
  }

  @Bean
  fun deleteAppImageUseCase(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      properties: AtomicAppImageProperties,
  ): DeleteAppImageUseCase {
    val service =
        AppImageApiService(
            imageMetadataPort = imageMetadataPort,
            imageObjectStoragePort = imageObjectStoragePort,
            requestPolicy = requestPolicy(properties),
        )
    return DeleteAppImageUseCase { command -> service.deleteImage(command) }
  }

  @Bean
  @ConditionalOnMissingBean
  fun inspectDeletePendingImagesUseCase(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      clockProvider: ObjectProvider<Clock>,
  ): InspectDeletePendingImagesUseCase {
    val service =
        AppImageDeleteRecoveryService(
            imageMetadataPort = imageMetadataPort,
            imageObjectStoragePort = imageObjectStoragePort,
            clock = clockProvider.getIfAvailable { Clock.systemUTC() },
        )
    return object : InspectDeletePendingImagesUseCase {
      override fun inspectDeletePendingImages() = service.inspectDeletePendingImages()
    }
  }

  @Bean
  @ConditionalOnMissingBean
  fun recoverDeletePendingImagesUseCase(
      imageMetadataPort: ImageMetadataPort,
      imageObjectStoragePort: ImageObjectStoragePort,
      clockProvider: ObjectProvider<Clock>,
  ): RecoverDeletePendingImagesUseCase {
    val service =
        AppImageDeleteRecoveryService(
            imageMetadataPort = imageMetadataPort,
            imageObjectStoragePort = imageObjectStoragePort,
            clock = clockProvider.getIfAvailable { Clock.systemUTC() },
        )
    return object : RecoverDeletePendingImagesUseCase {
      override fun recoverDeletePendingImages(limit: Int) =
          service.recoverDeletePendingImages(limit)
    }
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
