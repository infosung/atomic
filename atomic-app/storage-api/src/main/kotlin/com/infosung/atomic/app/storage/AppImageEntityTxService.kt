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

  /** Marks metadata row as delete-pending before storage cleanup. */
  @Transactional
  open fun markDeletePending(imageEntity: ImageEntity): ImageEntity {
    if (imageEntity.status == ImageEntity.STATUS_DELETE_PENDING) {
      log.info(
          "Image metadata already delete-pending: imageId={}, objectKey={}",
          imageEntity.id,
          imageEntity.fileName,
      )
      return imageEntity
    }
    val pendingEntity = imageEntity.toDeletePending()
    log.info(
        "Marking image metadata delete-pending: imageId={}, objectKey={}, previousStatus={}, nextStatus={}",
        imageEntity.id,
        imageEntity.fileName,
        imageEntity.status,
        pendingEntity.status,
    )
    return save(pendingEntity)
  }

  /** Deletes image metadata row. */
  @Transactional
  open fun delete(imageEntity: ImageEntity) {
    log.debug(
        "Deleting image metadata: imageId={}, objectKey={}", imageEntity.id, imageEntity.fileName)
    imageRepository.delete(imageEntity)
  }

  /** Deletes metadata row after storage cleanup succeeded. */
  @Transactional
  open fun purgeDeletePending(imageEntity: ImageEntity) {
    log.info(
        "Purging delete-pending image metadata: imageId={}, objectKey={}, status={}",
        imageEntity.id,
        imageEntity.fileName,
        imageEntity.status,
    )
    delete(imageEntity)
  }
}
