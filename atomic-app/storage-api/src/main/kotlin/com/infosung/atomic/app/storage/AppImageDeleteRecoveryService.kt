package com.infosung.atomic.app.storage

import com.infosung.atomic.contract.log.LogStringPreview
import com.infosung.atomic.storage.image.ImageService
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.slf4j.LoggerFactory

/** Retries cleanup for metadata rows that remain in `DELETE_PENDING`. */
class AppImageDeleteRecoveryService(
    private val imageEntityTxService: AppImageEntityTxService,
    private val imageService: ImageService,
    private val clock: Clock = Clock.systemUTC(),
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun inspectDeletePendingImages(): ImageDeletePendingSnapshot {
    val snapshot = imageEntityTxService.inspectDeletePendingImages()
    log.debug(
        "Inspected delete-pending image metadata snapshot: pendingCount={}, oldestPendingCreatedAt={}, oldestPendingAgeSeconds={}",
        snapshot.pendingCount,
        snapshot.oldestPendingCreatedAt,
        oldestPendingAgeSeconds(snapshot.oldestPendingCreatedAt),
    )
    return snapshot
  }

  fun recoverDeletePendingImages(limit: Int = DEFAULT_RECOVERY_LIMIT): ImageDeleteRecoveryResult {
    require(limit > 0) { "limit must be greater than zero." }

    val beforeSnapshot = inspectDeletePendingImages()
    val batchToken = UUID.randomUUID().toString()
    val claimedAt = LocalDateTime.now(clock)
    val pendingImages =
        imageEntityTxService.claimDeletePending(
            limit = limit,
            claimToken = batchToken,
            claimedAt = claimedAt,
        )
    log.info(
        "Starting image delete recovery batch: requestedLimit={}, claimToken={}, claimedAt={}, claimedCount={}, pendingCountBefore={}, oldestPendingCreatedAtBefore={}, oldestPendingAgeSecondsBefore={}",
        limit,
        batchToken,
        claimedAt,
        pendingImages.size,
        beforeSnapshot.pendingCount,
        beforeSnapshot.oldestPendingCreatedAt,
        oldestPendingAgeSeconds(beforeSnapshot.oldestPendingCreatedAt),
    )

    if (pendingImages.isEmpty()) {
      log.info(
          "No claimable delete-pending image metadata rows were found for recovery: requestedLimit={}, claimToken={}, pendingCountBefore={}",
          limit,
          batchToken,
          beforeSnapshot.pendingCount,
      )
    }

    var recoveredCount = 0
    var failedCount = 0
    pendingImages.forEach { imageEntity ->
      val imageId = imageEntity.id
      log.info(
          "Recovering claimed delete-pending image metadata: imageId={}, claimToken={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}, status={}",
          imageId,
          batchToken,
          imageEntity.storageType,
          LogStringPreview.summarize(imageEntity.fileName),
          LogStringPreview.summarize(imageEntity.thumbnailFileName),
          imageEntity.status,
      )
      try {
        imageService.deleteImage(
            storageType = imageEntity.storageType,
            fileName = imageEntity.fileName,
            thumbnailFileName = imageEntity.thumbnailFileName,
        )
        log.info(
            "Recovered storage cleanup for claimed delete-pending image: imageId={}, claimToken={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}",
            imageId,
            batchToken,
            imageEntity.storageType,
            LogStringPreview.summarize(imageEntity.fileName),
            LogStringPreview.summarize(imageEntity.thumbnailFileName),
        )
        imageEntityTxService.purgeDeletePending(imageEntity)
        recoveredCount += 1
      } catch (e: Exception) {
        failedCount += 1
        if (imageId != null) {
          imageEntityTxService.releaseDeleteRecoveryClaim(
              imageId = imageId,
              claimToken = batchToken,
          )
        }
        log.error(
            "Delete-pending recovery failed and claim was released for retry: imageId={}, claimToken={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}, status={}",
            imageId,
            batchToken,
            imageEntity.storageType,
            LogStringPreview.summarize(imageEntity.fileName),
            LogStringPreview.summarize(imageEntity.thumbnailFileName),
            imageEntity.status,
            e,
        )
      }
    }

    val afterSnapshot = inspectDeletePendingImages()
    return ImageDeleteRecoveryResult(
            scannedCount = pendingImages.size,
            recoveredCount = recoveredCount,
            failedCount = failedCount,
            remainingPendingCount = afterSnapshot.pendingCount,
            oldestPendingCreatedAt = afterSnapshot.oldestPendingCreatedAt,
        )
        .also { result ->
          log.info(
              "Completed image delete recovery batch: requestedLimit={}, claimToken={}, claimedCount={}, recoveredCount={}, failedCount={}, pendingCountAfter={}, oldestPendingCreatedAtAfter={}, oldestPendingAgeSecondsAfter={}",
              limit,
              batchToken,
              result.scannedCount,
              result.recoveredCount,
              result.failedCount,
              result.remainingPendingCount,
              result.oldestPendingCreatedAt,
              oldestPendingAgeSeconds(result.oldestPendingCreatedAt),
          )
          if (result.failedCount > 0 || result.remainingPendingCount > 0) {
            log.warn(
                "Delete-pending recovery finished with remaining work: requestedLimit={}, claimToken={}, failedCount={}, remainingPendingCount={}, oldestPendingCreatedAt={}, oldestPendingAgeSeconds={}",
                limit,
                batchToken,
                result.failedCount,
                result.remainingPendingCount,
                result.oldestPendingCreatedAt,
                oldestPendingAgeSeconds(result.oldestPendingCreatedAt),
            )
          }
        }
  }

  private fun oldestPendingAgeSeconds(oldestPendingCreatedAt: LocalDateTime?): Long? {
    if (oldestPendingCreatedAt == null) {
      return null
    }
    return ChronoUnit.SECONDS.between(oldestPendingCreatedAt, LocalDateTime.now(clock))
        .coerceAtLeast(0)
  }

  companion object {
    const val DEFAULT_RECOVERY_LIMIT: Int = 50
  }
}

data class ImageDeletePendingSnapshot(
    val pendingCount: Long,
    val oldestPendingCreatedAt: LocalDateTime?,
)

data class ImageDeleteRecoveryResult(
    val scannedCount: Int,
    val recoveredCount: Int,
    val failedCount: Int,
    val remainingPendingCount: Long,
    val oldestPendingCreatedAt: LocalDateTime?,
)
