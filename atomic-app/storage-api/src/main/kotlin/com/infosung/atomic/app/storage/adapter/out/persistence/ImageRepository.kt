package com.infosung.atomic.app.storage.adapter.out.persistence

import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Repository for stored image metadata. */
interface ImageRepository : JpaRepository<ImageEntity, UUID> {
  fun countByStatus(status: String): Long

  fun findAllByStatusOrderByCreatedAtAsc(
      status: String,
      pageable: Pageable,
  ): List<ImageEntity>

  fun findFirstByStatusOrderByCreatedAtAsc(status: String): ImageEntity?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      SELECT i
      FROM image i
      WHERE i.status = :status
        AND (
          i.deleteRecoveryClaimToken IS NULL
          OR i.deleteRecoveryClaimedAt IS NULL
          OR i.deleteRecoveryClaimedAt < :staleClaimBefore
        )
      ORDER BY i.createdAt ASC
      """,
  )
  fun findClaimableDeletePendingImages(
      @Param("status") status: String,
      @Param("staleClaimBefore") staleClaimBefore: LocalDateTime,
      pageable: Pageable,
  ): List<ImageEntity>

  @Modifying
  @Query(
      """
      UPDATE image i
      SET i.deleteRecoveryClaimToken = NULL,
          i.deleteRecoveryClaimedAt = NULL
      WHERE i.id = :imageId
        AND i.status = :status
        AND i.deleteRecoveryClaimToken = :claimToken
      """,
  )
  fun releaseDeleteRecoveryClaim(
      @Param("imageId") imageId: UUID,
      @Param("status") status: String,
      @Param("claimToken") claimToken: String,
  ): Int
}
