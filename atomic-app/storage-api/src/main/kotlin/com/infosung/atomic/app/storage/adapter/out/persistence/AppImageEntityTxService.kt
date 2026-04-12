package com.infosung.atomic.app.storage.adapter.out.persistence

import com.infosung.atomic.app.storage.application.exception.ImageNotFoundException
import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.contract.log.LogStringPreview
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

/** Transaction boundary adapter for image metadata persistence. */
open class AppImageEntityTxService(
    private val imageRepository: ImageRepository,
) : ImageMetadataPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional(readOnly = true)
  override fun findByIdOrThrow(
      imageId: UUID,
      rawImageId: String,
  ): StoredImage {
    log.debug("Loading image metadata: imageId={}", rawImageId)
    return imageRepository.findById(imageId).map(ImageEntityMapper::toDomain).orElseThrow {
      ImageNotFoundException("image not found: $rawImageId")
    }
  }

  @Transactional
  override fun save(image: StoredImage): StoredImage {
    log.debug(
        "Persisting image metadata: serviceName={}, storageService={}, objectKeyPreview={}",
        image.serviceName,
        image.storageService,
        LogStringPreview.summarize(image.fileName),
    )
    return ImageEntityMapper.toDomain(imageRepository.save(ImageEntityMapper.toEntity(image)))
  }

  @Transactional
  override fun markDeletePending(image: StoredImage): StoredImage {
    if (image.status == StoredImage.STATUS_DELETE_PENDING) {
      log.info(
          "Image metadata already delete-pending: imageId={}, objectKeyPreview={}",
          image.id,
          LogStringPreview.summarize(image.fileName),
      )
      return image
    }
    val pendingImage = image.toDeletePending()
    log.info(
        "Marking image metadata delete-pending: imageId={}, objectKeyPreview={}, previousStatus={}, nextStatus={}",
        image.id,
        LogStringPreview.summarize(image.fileName),
        image.status,
        pendingImage.status,
    )
    return save(pendingImage)
  }

  @Transactional
  override fun purgeDeletePending(image: StoredImage) {
    log.info(
        "Purging delete-pending image metadata: imageId={}, objectKeyPreview={}, status={}",
        image.id,
        LogStringPreview.summarize(image.fileName),
        image.status,
    )
    imageRepository.delete(ImageEntityMapper.toEntity(image))
  }

  @Transactional(readOnly = true)
  override fun inspectDeletePendingImages(): ImageDeletePendingSnapshot {
    val pendingCount = imageRepository.countByStatus(StoredImage.STATUS_DELETE_PENDING)
    val oldestPendingCreatedAt =
        imageRepository
            .findFirstByStatusOrderByCreatedAtAsc(StoredImage.STATUS_DELETE_PENDING)
            ?.createdAt
    log.debug(
        "Loaded delete-pending image metadata snapshot: pendingCount={}, oldestPendingCreatedAt={}",
        pendingCount,
        oldestPendingCreatedAt,
    )
    return ImageDeletePendingSnapshot(
        pendingCount = pendingCount,
        oldestPendingCreatedAt = oldestPendingCreatedAt,
    )
  }

  @Transactional
  override fun claimDeletePending(
      limit: Int,
      claimToken: String,
      claimedAt: LocalDateTime,
  ): List<StoredImage> {
    val staleClaimBefore = claimedAt.minusSeconds(DEFAULT_DELETE_RECOVERY_CLAIM_TIMEOUT_SECONDS)
    log.info(
        "Claiming delete-pending image metadata batch: limit={}, claimToken={}, claimedAt={}, staleClaimBefore={}",
        limit,
        claimToken,
        claimedAt,
        staleClaimBefore,
    )
    val claimedEntities =
        imageRepository.findClaimableDeletePendingImages(
            status = StoredImage.STATUS_DELETE_PENDING,
            staleClaimBefore = staleClaimBefore,
            pageable = PageRequest.of(0, limit),
        )
    claimedEntities.forEach { entity ->
      entity.deleteRecoveryClaimToken = claimToken
      entity.deleteRecoveryClaimedAt = claimedAt
    }
    imageRepository.flush()
    val claimedRows = claimedEntities.map(ImageEntityMapper::toDomain)

    log.info(
        "Claimed delete-pending image metadata batch: claimToken={}, claimedCount={}, claimedImageIds={}",
        claimToken,
        claimedRows.size,
        claimedRows.mapNotNull { it.id },
    )
    return claimedRows
  }

  @Transactional
  override fun releaseDeleteRecoveryClaim(
      imageId: UUID,
      claimToken: String,
  ) {
    val releasedCount =
        imageRepository.releaseDeleteRecoveryClaim(
            imageId = imageId,
            status = StoredImage.STATUS_DELETE_PENDING,
            claimToken = claimToken,
        )
    if (releasedCount > 0) {
      log.info(
          "Released delete-pending recovery claim: imageId={}, claimToken={}, releasedCount={}",
          imageId,
          claimToken,
          releasedCount,
      )
    } else {
      log.debug(
          "Delete-pending recovery claim release skipped: imageId={}, claimToken={}, releasedCount={}",
          imageId,
          claimToken,
          releasedCount,
      )
    }
  }

  private companion object {
    const val DEFAULT_DELETE_RECOVERY_CLAIM_TIMEOUT_SECONDS: Long = 900
  }
}
