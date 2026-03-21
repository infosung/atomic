package com.infosung.atomic.app.storage.adapter.out.persistence

import com.infosung.atomic.app.storage.application.exception.ImageNotFoundException
import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.contract.log.LogStringPreview
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

/** Transaction boundary adapter for image metadata persistence. */
open class AppImageEntityTxService(
    private val imageRepository: ImageRepository,
    private val jdbcTemplate: JdbcTemplate? = null,
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
    imageRepository.flush()
    val staleClaimBefore = claimedAt.minusSeconds(DEFAULT_DELETE_RECOVERY_CLAIM_TIMEOUT_SECONDS)
    log.info(
        "Claiming delete-pending image metadata batch: limit={}, claimToken={}, claimedAt={}, staleClaimBefore={}",
        limit,
        claimToken,
        claimedAt,
        staleClaimBefore,
    )
    val claimedRows =
        jdbcTemplate()
            .query(
                CLAIM_DELETE_PENDING_SQL,
                { rs, _ ->
                  ImageEntityMapper.toDomain(
                      ImageEntity(
                          id = UUID.fromString(rs.getString("id")),
                          bucket = rs.getString("bucket"),
                          serviceName = rs.getString("service_name"),
                          storageService = rs.getString("storage_service"),
                          status = rs.getString("status"),
                          uploaderId = rs.getString("uploader_id"),
                          storageType = rs.getString("storage_type"),
                          fileName = rs.getString("file_name"),
                          thumbnailFileName = rs.getString("thumbnail_file_name"),
                          url = rs.getString("url"),
                          thumbnailUrl = rs.getString("thumbnail_url"),
                          width = rs.getObject("width")?.let { (it as Number).toInt() },
                          height = rs.getObject("height")?.let { (it as Number).toInt() },
                          fileSize = (rs.getObject("file_size") as Number).toLong(),
                          thumbnailWidth =
                              rs.getObject("thumbnail_width")?.let { (it as Number).toInt() },
                          thumbnailHeight =
                              rs.getObject("thumbnail_height")?.let { (it as Number).toInt() },
                          thumbnailFileSize =
                              rs.getObject("thumbnail_file_size")?.let { (it as Number).toLong() },
                          createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
                      ),
                  )
                },
                StoredImage.STATUS_DELETE_PENDING,
                Timestamp.valueOf(staleClaimBefore),
                limit,
                claimToken,
                Timestamp.valueOf(claimedAt),
                Timestamp.valueOf(staleClaimBefore),
            )

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
        jdbcTemplate()
            .update(
                RELEASE_DELETE_PENDING_CLAIM_SQL,
                imageId.toString(),
                StoredImage.STATUS_DELETE_PENDING,
                claimToken,
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

  private fun jdbcTemplate(): JdbcTemplate {
    return requireNotNull(jdbcTemplate) {
      "AppImageEntityTxService requires JdbcTemplate for delete-pending claim operations."
    }
  }

  private companion object {
    const val DEFAULT_DELETE_RECOVERY_CLAIM_TIMEOUT_SECONDS: Long = 900

    val CLAIM_DELETE_PENDING_SQL =
        """
        WITH candidates AS (
          SELECT id
          FROM image
          WHERE status = ?
            AND (
              delete_recovery_claim_token IS NULL
              OR delete_recovery_claimed_at IS NULL
              OR delete_recovery_claimed_at < ?
            )
          ORDER BY created_at ASC
          LIMIT ?
        ),
        claimed AS (
          UPDATE image AS i
          SET delete_recovery_claim_token = ?,
              delete_recovery_claimed_at = ?
          FROM candidates
          WHERE i.id = candidates.id
            AND (
              i.delete_recovery_claim_token IS NULL
              OR i.delete_recovery_claimed_at IS NULL
              OR i.delete_recovery_claimed_at < ?
            )
          RETURNING i.*
        )
        SELECT *
        FROM claimed
        ORDER BY created_at ASC
        """
            .trimIndent()

    val RELEASE_DELETE_PENDING_CLAIM_SQL =
        """
        UPDATE image
        SET delete_recovery_claim_token = NULL,
            delete_recovery_claimed_at = NULL
        WHERE id = ?
          AND status = ?
          AND delete_recovery_claim_token = ?
        """
            .trimIndent()
  }
}
