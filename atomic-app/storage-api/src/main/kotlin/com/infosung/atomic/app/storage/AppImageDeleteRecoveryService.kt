package com.infosung.atomic.app.storage

import com.infosung.atomic.storage.image.ImageService
import org.slf4j.LoggerFactory

/** Retries cleanup for metadata rows that remain in `DELETE_PENDING`. */
class AppImageDeleteRecoveryService(
    private val imageEntityTxService: AppImageEntityTxService,
    private val imageService: ImageService,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun recoverDeletePendingImages(limit: Int = DEFAULT_RECOVERY_LIMIT): ImageDeleteRecoveryResult {
    require(limit > 0) { "limit must be greater than zero." }

    val pendingImages = imageEntityTxService.findDeletePending(limit)
    log.info(
        "Starting image delete recovery batch: requestedLimit={}, scannedCount={}",
        limit,
        pendingImages.size,
    )

    var recoveredCount = 0
    var failedCount = 0
    pendingImages.forEach { imageEntity ->
      val imageId = imageEntity.id
      log.info(
          "Recovering delete-pending image metadata: imageId={}, storageType={}, fileName={}, thumbnailFileName={}, status={}",
          imageId,
          imageEntity.storageType,
          imageEntity.fileName,
          imageEntity.thumbnailFileName,
          imageEntity.status,
      )
      try {
        imageService.deleteImage(
            storageType = imageEntity.storageType,
            fileName = imageEntity.fileName,
            thumbnailFileName = imageEntity.thumbnailFileName,
        )
        log.info(
            "Recovered storage cleanup for delete-pending image: imageId={}, storageType={}, fileName={}, thumbnailFileName={}",
            imageId,
            imageEntity.storageType,
            imageEntity.fileName,
            imageEntity.thumbnailFileName,
        )
        imageEntityTxService.purgeDeletePending(imageEntity)
        recoveredCount += 1
      } catch (e: Exception) {
        failedCount += 1
        log.error(
            "Delete-pending recovery failed: imageId={}, storageType={}, fileName={}, thumbnailFileName={}",
            imageId,
            imageEntity.storageType,
            imageEntity.fileName,
            imageEntity.thumbnailFileName,
            e,
        )
      }
    }

    return ImageDeleteRecoveryResult(
            scannedCount = pendingImages.size,
            recoveredCount = recoveredCount,
            failedCount = failedCount,
        )
        .also { result ->
          log.info(
              "Completed image delete recovery batch: requestedLimit={}, scannedCount={}, recoveredCount={}, failedCount={}",
              limit,
              result.scannedCount,
              result.recoveredCount,
              result.failedCount,
          )
        }
  }

  companion object {
    const val DEFAULT_RECOVERY_LIMIT: Int = 50
  }
}

data class ImageDeleteRecoveryResult(
    val scannedCount: Int,
    val recoveredCount: Int,
    val failedCount: Int,
)
