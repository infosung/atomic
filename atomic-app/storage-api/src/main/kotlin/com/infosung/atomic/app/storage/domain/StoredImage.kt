package com.infosung.atomic.app.storage.domain

import java.time.LocalDateTime
import java.util.UUID

/** Stored image metadata used by the storage application layer. */
data class StoredImage(
    val id: UUID? = null,
    val bucket: String,
    val serviceName: String,
    val storageService: String,
    val status: String = STATUS_ACTIVE,
    val uploaderId: String? = null,
    val storageType: String,
    val fileName: String? = null,
    val thumbnailFileName: String? = null,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSize: Long,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val thumbnailFileSize: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
  fun toDeletePending(): StoredImage {
    return copy(status = STATUS_DELETE_PENDING)
  }

  companion object {
    const val STATUS_ACTIVE: String = "ACTIVE"
    const val STATUS_DELETE_PENDING: String = "DELETE_PENDING"
  }
}
