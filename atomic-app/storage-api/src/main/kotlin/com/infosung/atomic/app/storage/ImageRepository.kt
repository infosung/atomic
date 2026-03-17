package com.infosung.atomic.app.storage

import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/** Repository for uploaded image metadata. */
interface ImageRepository : JpaRepository<ImageEntity, UUID> {
  fun countByStatus(status: String): Long

  fun findAllByStatusOrderByCreatedAtAsc(
      status: String,
      pageable: Pageable,
  ): List<ImageEntity>

  fun findFirstByStatusOrderByCreatedAtAsc(status: String): ImageEntity?
}
