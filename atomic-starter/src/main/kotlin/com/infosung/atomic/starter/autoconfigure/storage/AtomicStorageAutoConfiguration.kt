package com.infosung.atomic.starter.autoconfigure.storage

import com.infosung.atomic.starter.autoconfigure.contract.AtomicContractAutoConfiguration
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.CommonsImagingImageInputValidator
import com.infosung.atomic.storage.image.CommonsImagingMetadataReader
import com.infosung.atomic.storage.image.DefaultImageObjectKeyGenerator
import com.infosung.atomic.storage.image.DefaultImageThumbnailGenerator
import com.infosung.atomic.storage.image.ImageInputValidator
import com.infosung.atomic.storage.image.ImageMetadataReader
import com.infosung.atomic.storage.image.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.image.ImageThumbnailGenerator
import com.infosung.atomic.storage.s3.S3ClientFactory
import com.infosung.atomic.storage.s3.S3ClientSettings
import com.infosung.atomic.storage.s3.S3CompatibleStorageClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** Auto-configuration for atomic storage module. */
@AutoConfiguration(after = [AtomicContractAutoConfiguration::class])
@ConditionalOnClass(
    name =
        [
            "com.infosung.atomic.storage.image.ImageService",
            "com.infosung.atomic.storage.s3.S3ClientFactory",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.storage",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(AtomicStorageProperties::class)
class AtomicStorageAutoConfiguration {
  /** Registers storage clients map keyed by `storageType`. */
  @Bean("storageClients")
  @ConditionalOnMissingBean(name = ["storageClients"])
  fun storageClients(properties: AtomicStorageProperties): Map<String, StorageClient> {
    return properties.backends.entries
        .asSequence()
        .filter { (_, backend) -> backend.enabled }
        .associate { (storageType, backend) ->
          val backendType = backend.type.lowercase()
          require(backendType in SUPPORTED_S3_COMPATIBLE_BACKEND_TYPES) {
            "Unsupported storage backend type for '$storageType': ${backend.type}. " +
                "Supported types: ${SUPPORTED_S3_COMPATIBLE_BACKEND_TYPES.joinToString(", ")}."
          }
          require(backend.bucket.isNotBlank()) {
            "atomic.storage.backends.$storageType.bucket must not be blank."
          }
          require(backend.region.isNotBlank()) {
            "atomic.storage.backends.$storageType.region must not be blank."
          }

          val s3Client =
              S3ClientFactory.create(
                  S3ClientSettings(
                      region = backend.region,
                      endpoint = backend.endpoint,
                      pathStyleAccessEnabled = backend.pathStyleAccessEnabled,
                      accessKeyId = backend.accessKeyId,
                      secretAccessKey = backend.secretAccessKey,
                      sessionToken = backend.sessionToken,
                  ),
              )
          storageType to S3CompatibleStorageClient(s3Client = s3Client, bucket = backend.bucket)
        }
  }

  /** Registers storage profiles map keyed by `storageType`. */
  @Bean("storageProfiles")
  @ConditionalOnMissingBean(name = ["storageProfiles"])
  fun storageProfiles(properties: AtomicStorageProperties): Map<String, StorageProfile> {
    return properties.backends.entries
        .asSequence()
        .filter { (_, backend) -> backend.enabled }
        .associate { (storageType, backend) ->
          require(backend.bucket.isNotBlank()) {
            "atomic.storage.backends.$storageType.bucket must not be blank."
          }
          require(backend.cdn.isNotBlank()) {
            "atomic.storage.backends.$storageType.cdn must not be blank."
          }
          storageType to
              StorageProfile(
                  bucket = backend.bucket,
                  cdn = backend.cdn,
                  prependBucketOnObjectKey = backend.prependBucketOnObjectKey,
              )
        }
  }

  /** Registers [ImageService] wired with configured storage client/profile maps. */
  @Bean
  @ConditionalOnMissingBean
  fun imageService(
      @Qualifier("storageClients") storageClients: Map<String, StorageClient>,
      @Qualifier("storageProfiles") storageProfiles: Map<String, StorageProfile>,
      objectKeyGeneratorProvider: ObjectProvider<ImageObjectKeyGenerator>,
      imageInputValidatorProvider: ObjectProvider<ImageInputValidator>,
      metadataReaderProvider: ObjectProvider<ImageMetadataReader>,
      thumbnailGeneratorProvider: ObjectProvider<ImageThumbnailGenerator>,
  ): ImageService {
    val resolvedObjectKeyGenerator =
        objectKeyGeneratorProvider.getIfAvailable { DefaultImageObjectKeyGenerator() }
    val resolvedImageInputValidator =
        imageInputValidatorProvider.getIfAvailable { CommonsImagingImageInputValidator() }
    val resolvedMetadataReader =
        metadataReaderProvider.getIfAvailable { CommonsImagingMetadataReader() }
    val resolvedThumbnailGenerator =
        thumbnailGeneratorProvider.getIfAvailable {
          DefaultImageThumbnailGenerator(metadataReader = resolvedMetadataReader)
        }

    return ImageService(
        storageClients = storageClients,
        storageProfiles = storageProfiles,
        objectKeyGenerator = resolvedObjectKeyGenerator,
        imageInputValidator = resolvedImageInputValidator,
        metadataReader = resolvedMetadataReader,
        thumbnailGenerator = resolvedThumbnailGenerator,
    )
  }

  private companion object {
    val SUPPORTED_S3_COMPATIBLE_BACKEND_TYPES: Set<String> = setOf("s3", "r2", "minio")
  }
}
