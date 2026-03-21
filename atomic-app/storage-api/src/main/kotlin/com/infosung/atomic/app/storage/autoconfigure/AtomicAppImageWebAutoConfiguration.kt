package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageController
import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageHttpExceptionHandler
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

@AutoConfiguration
class AtomicAppImageWebAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun appStorageController(
      uploadAppImageUseCaseProvider: ObjectProvider<UploadAppImageUseCase>,
      deleteAppImageUseCaseProvider: ObjectProvider<DeleteAppImageUseCase>,
      properties: AtomicAppImageProperties,
  ): AppStorageController {
    val uploadAppImageUseCase =
        uploadAppImageUseCaseProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.app.image.enabled=true requires UploadAppImageUseCase. Ensure storage/image prerequisites are configured.",
                )
                .also {
                  log.error(
                      "Image API auto-configuration fail-fast: UploadAppImageUseCase is missing while atomic.app.image.enabled=true.",
                  )
                }
    val deleteAppImageUseCase =
        deleteAppImageUseCaseProvider.getIfAvailable()
            ?: throw IllegalStateException(
                    "atomic.app.image.enabled=true requires DeleteAppImageUseCase. Ensure storage/image prerequisites are configured.",
                )
                .also {
                  log.error(
                      "Image API auto-configuration fail-fast: DeleteAppImageUseCase is missing while atomic.app.image.enabled=true.",
                  )
                }

    return AppStorageController(
        uploadAppImageUseCase = uploadAppImageUseCase,
        deleteAppImageUseCase = deleteAppImageUseCase,
        properties = properties,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun appStorageHttpExceptionHandler(): AppStorageHttpExceptionHandler {
    return AppStorageHttpExceptionHandler()
  }
}
