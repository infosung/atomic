package com.infosung.atomic.app.storage.adapter.out.persistence

import com.infosung.atomic.app.storage.domain.StoredImage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes

/** Stored image metadata entity. */
@Entity(name = "image")
@Table(name = "image")
class ImageEntity(
    @Id @Column(name = "id") @UuidGenerator @JdbcTypeCode(SqlTypes.VARCHAR) val id: UUID? = null,
    @Column(name = "bucket", length = 255) val bucket: String,
    @Column(name = "service_name", length = 255) val serviceName: String,
    @Column(name = "storage_service", length = 255) val storageService: String,
    @Column(name = "status") val status: String = StoredImage.STATUS_ACTIVE,
    @Column(name = "uploader_id") val uploaderId: String? = null,
    @Column(name = "storage_type", length = 255) val storageType: String,
    @Column(name = "file_name") @JdbcTypeCode(SqlTypes.LONGVARCHAR) val fileName: String? = null,
    @Column(name = "thumbnail_file_name")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    val thumbnailFileName: String? = null,
    @Column(name = "url") @JdbcTypeCode(SqlTypes.LONGVARCHAR) val url: String,
    @Column(name = "thumbnail_url")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    val thumbnailUrl: String? = null,
    @Column(name = "width") val width: Int? = null,
    @Column(name = "height") val height: Int? = null,
    @Column(name = "file_size") val fileSize: Long,
    @Column(name = "thumbnail_width") val thumbnailWidth: Int? = null,
    @Column(name = "thumbnail_height") val thumbnailHeight: Int? = null,
    @Column(name = "thumbnail_file_size") val thumbnailFileSize: Long? = null,
    @Column(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
) {
  @Column(name = "delete_recovery_claim_token", length = 255)
  var deleteRecoveryClaimToken: String? = null

  @Column(name = "delete_recovery_claimed_at") var deleteRecoveryClaimedAt: LocalDateTime? = null
}
