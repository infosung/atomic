package com.infosung.atomic.app.storage

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UuidGenerator
import org.hibernate.type.SqlTypes

/** Stored image metadata. */
@Entity(name = "image")
class ImageEntity(
    @Id @UuidGenerator @JdbcTypeCode(SqlTypes.VARCHAR) val id: UUID? = null,
    val bucket: String,
    val serviceName: String,
    val storageService: String,
    val status: String = "ACTIVE",
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
)
