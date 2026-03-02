package com.infosung.atomic.app.storage

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/** Transaction boundary service for image metadata persistence. */
open class AppImageEntityTxService(
    private val imageRepository: ImageRepository,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /** Reads image metadata row by id. */
  @Transactional(readOnly = true)
  open fun findByIdOrThrow(
      imageId: UUID,
      rawImageId: String,
  ): ImageEntity {
    log.debug("Loading image metadata: imageId={}", rawImageId)
    return imageRepository.findById(imageId).orElseThrow {
      IllegalArgumentException("image not found: $rawImageId")
    }
  }

  /** Persists uploaded image metadata row. */
  @Transactional
  open fun save(imageEntity: ImageEntity): ImageEntity {
    log.debug(
        "Persisting image metadata: serviceName={}, storageService={}, objectKey={}",
        imageEntity.serviceName,
        imageEntity.storageService,
        imageEntity.fileName,
    )
    return imageRepository.save(imageEntity)
  }

  /** Deletes image metadata row. */
  @Transactional
  open fun delete(imageEntity: ImageEntity) {
    log.debug(
        "Deleting image metadata: imageId={}, objectKey={}", imageEntity.id, imageEntity.fileName)
    imageRepository.delete(imageEntity)
  }
}
