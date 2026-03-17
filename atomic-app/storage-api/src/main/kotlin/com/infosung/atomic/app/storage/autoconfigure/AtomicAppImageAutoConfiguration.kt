package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.AppImageApiService
import com.infosung.atomic.app.storage.AppImageDeleteRecoveryService
import com.infosung.atomic.app.storage.AppImageEntityTxService
import com.infosung.atomic.app.storage.AppStorageController
import com.infosung.atomic.app.storage.AppStorageHttpExceptionHandler
import com.infosung.atomic.app.storage.ImageEntity
import com.infosung.atomic.app.storage.ImageRepository
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.image.ImageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate

/** Auto-configuration for common image upload/delete API. */
@AutoConfiguration(
    afterName =
        ["com.infosung.atomic.starter.autoconfigure.storage.AtomicStorageAutoConfiguration"])
@ConditionalOnClass(
    name =
        [
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.multipart.MultipartFile",
            "org.springframework.data.jpa.repository.JpaRepository",
            "jakarta.persistence.Entity",
        ],
)
@ConditionalOnProperty(
    prefix = "atomic.app.image",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicAppImageProperties::class)
@EntityScan(basePackageClasses = [ImageEntity::class])
@EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
class AtomicAppImageAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun appImageEntityTxService(
      imageRepository: ImageRepository,
  ): AppImageEntityTxService {
    return AppImageEntityTxService(imageRepository = imageRepository)
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ImageService::class)
  fun appImageApiService(
      appImageEntityTxService: AppImageEntityTxService,
      imageService: ImageService,
      @Qualifier("storageClients") storageClients: Map<String, StorageClient>,
      properties: AtomicAppImageProperties,
  ): AppImageApiService {
    return AppImageApiService(
        imageEntityTxService = appImageEntityTxService,
        imageService = imageService,
        storageClients = storageClients,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(ImageService::class)
  fun appImageDeleteRecoveryService(
      appImageEntityTxService: AppImageEntityTxService,
      imageService: ImageService,
  ): AppImageDeleteRecoveryService {
    return AppImageDeleteRecoveryService(
        imageEntityTxService = appImageEntityTxService,
        imageService = imageService,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun appImageSchemaUpgradePreflight(
      jdbcTemplate: JdbcTemplate,
  ): AppImageSchemaUpgradePreflight {
    return AppImageSchemaUpgradePreflight(jdbcTemplate = jdbcTemplate)
  }

  /**
   * Registers image API controller.
   *
   * Fail-fast policy:
   * - When `atomic.app.image.enabled=true`, controller requires AppImageApiService.
   * - Missing AppImageApiService (for example storage/image prerequisites are absent) fails startup
   *   with explicit guidance.
   */
  @Bean
  @ConditionalOnMissingBean
  fun appStorageController(
      appImageApiServiceProvider: ObjectProvider<AppImageApiService>,
      properties: AtomicAppImageProperties,
  ): AppStorageController {
    val appImageApiService =
        appImageApiServiceProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.app.image.enabled=true requires AppImageApiService. " +
                        "Ensure ImageService and storageClients are configured (for starter usage: atomic.storage.enabled=true).",
                )
                .also {
                  log.error(
                      "Image API auto-configuration fail-fast: AppImageApiService is missing while atomic.app.image.enabled=true.",
                  )
                }
    return AppStorageController(
        appImageApiService = appImageApiService,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun appStorageHttpExceptionHandler(): AppStorageHttpExceptionHandler {
    return AppStorageHttpExceptionHandler()
  }
}
