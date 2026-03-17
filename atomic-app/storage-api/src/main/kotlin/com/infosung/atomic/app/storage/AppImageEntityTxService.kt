package com.infosung.atomic.app.storage

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

/** Transaction boundary service for image metadata persistence. */
open class AppImageEntityTxService(
    private val imageRepository: ImageRepository,
    private val jdbcTemplate: JdbcTemplate? = null,
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

  /** Reads oldest delete-pending metadata rows for recovery. */
  @Transactional(readOnly = true)
  open fun findDeletePending(limit: Int): List<ImageEntity> {
    log.debug("Loading delete-pending image metadata batch: limit={}", limit)
    return imageRepository.findAllByStatusOrderByCreatedAtAsc(
        status = ImageEntity.STATUS_DELETE_PENDING,
        pageable = PageRequest.of(0, limit),
    )
  }

  /** Claims oldest delete-pending rows for one recovery batch. */
  @Transactional
  open fun claimDeletePending(
      limit: Int,
      claimToken: String,
      claimedAt: LocalDateTime,
  ): List<ImageEntity> {
    // Flush pending JPA writes before the JDBC claim query so the batch sees newly-saved rows.
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
                  )
                },
                ImageEntity.STATUS_DELETE_PENDING,
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

  /** Returns a lightweight operator snapshot for current delete-pending work. */
  @Transactional(readOnly = true)
  open fun inspectDeletePendingImages(): ImageDeletePendingSnapshot {
    val pendingCount = imageRepository.countByStatus(ImageEntity.STATUS_DELETE_PENDING)
    val oldestPendingCreatedAt =
        imageRepository
            .findFirstByStatusOrderByCreatedAtAsc(ImageEntity.STATUS_DELETE_PENDING)
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

  /** Persists uploaded image metadata row. */
  @Transactional
  open fun save(imageEntity: ImageEntity): ImageEntity {
    log.debug(
        "Persisting image metadata: serviceName={}, storageService={}, objectKeyPreview={}",
        imageEntity.serviceName,
        imageEntity.storageService,
        summarizeForLog(imageEntity.fileName),
    )
    return imageRepository.save(imageEntity)
  }

  /** Marks metadata row as delete-pending before storage cleanup. */
  @Transactional
  open fun markDeletePending(imageEntity: ImageEntity): ImageEntity {
    if (imageEntity.status == ImageEntity.STATUS_DELETE_PENDING) {
      log.info(
          "Image metadata already delete-pending: imageId={}, objectKeyPreview={}",
          imageEntity.id,
          summarizeForLog(imageEntity.fileName),
      )
      return imageEntity
    }
    val pendingEntity = imageEntity.toDeletePending()
    log.info(
        "Marking image metadata delete-pending: imageId={}, objectKeyPreview={}, previousStatus={}, nextStatus={}",
        imageEntity.id,
        summarizeForLog(imageEntity.fileName),
        imageEntity.status,
        pendingEntity.status,
    )
    return save(pendingEntity)
  }

  /** Deletes image metadata row. */
  @Transactional
  open fun delete(imageEntity: ImageEntity) {
    log.debug(
        "Deleting image metadata: imageId={}, objectKeyPreview={}",
        imageEntity.id,
        summarizeForLog(imageEntity.fileName),
    )
    imageRepository.delete(imageEntity)
  }

  /** Deletes metadata row after storage cleanup succeeded. */
  @Transactional
  open fun purgeDeletePending(imageEntity: ImageEntity) {
    log.info(
        "Purging delete-pending image metadata: imageId={}, objectKeyPreview={}, status={}",
        imageEntity.id,
        summarizeForLog(imageEntity.fileName),
        imageEntity.status,
    )
    delete(imageEntity)
  }

  /** Releases recovery claim for a retryable delete-pending row. */
  @Transactional
  open fun releaseDeleteRecoveryClaim(
      imageId: UUID,
      claimToken: String,
  ) {
    val releasedCount =
        jdbcTemplate()
            .update(
                RELEASE_DELETE_PENDING_CLAIM_SQL,
                imageId.toString(),
                ImageEntity.STATUS_DELETE_PENDING,
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

  private fun summarizeForLog(value: String?): String? {
    if (value == null) {
      return null
    }
    return if (value.length <= 96) value else value.take(93) + "..."
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
