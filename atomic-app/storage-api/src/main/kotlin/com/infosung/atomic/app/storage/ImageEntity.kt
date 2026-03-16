package com.infosung.atomic.app.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes

/** Stored image metadata. */
@Entity(name = "image")
@Table(name = "image")
class ImageEntity(
    @Id @Column(name = "id") @UuidGenerator @JdbcTypeCode(SqlTypes.VARCHAR) val id: UUID? = null,
    @Column(name = "bucket") val bucket: String,
    @Column(name = "service_name") val serviceName: String,
    @Column(name = "storage_service") val storageService: String,
    @Column(name = "status") val status: String = STATUS_ACTIVE,
    @Column(name = "uploader_id") val uploaderId: String? = null,
    @Column(name = "storage_type") val storageType: String,
    @Column(name = "file_name") val fileName: String? = null,
    @Column(name = "thumbnail_file_name") val thumbnailFileName: String? = null,
    @Column(name = "url") val url: String,
    @Column(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Column(name = "width") val width: Int? = null,
    @Column(name = "height") val height: Int? = null,
    @Column(name = "file_size") val fileSize: Long,
    @Column(name = "thumbnail_width") val thumbnailWidth: Int? = null,
    @Column(name = "thumbnail_height") val thumbnailHeight: Int? = null,
    @Column(name = "thumbnail_file_size") val thumbnailFileSize: Long? = null,
    @Column(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
) {
  fun toDeletePending(): ImageEntity {
    return ImageEntity(
        id = id,
        bucket = bucket,
        serviceName = serviceName,
        storageService = storageService,
        status = STATUS_DELETE_PENDING,
        uploaderId = uploaderId,
        storageType = storageType,
        fileName = fileName,
        thumbnailFileName = thumbnailFileName,
        url = url,
        thumbnailUrl = thumbnailUrl,
        width = width,
        height = height,
        fileSize = fileSize,
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        thumbnailFileSize = thumbnailFileSize,
        createdAt = createdAt,
    )
  }

  companion object {
    const val STATUS_ACTIVE: String = "ACTIVE"
    const val STATUS_DELETE_PENDING: String = "DELETE_PENDING"
  }
}
