package com.infosung.atomic.app.storage.adapter.`in`.web

import com.infosung.atomic.app.storage.domain.StoredImage
import java.time.LocalDateTime
import java.util.UUID

/** External image metadata response model for the image API boundary. */
data class ImageResponse(
    val id: UUID? = null,
    val bucket: String,
    val serviceName: String,
    val storageService: String,
    val status: String = StoredImage.STATUS_ACTIVE,
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
  companion object {
    fun from(image: StoredImage): ImageResponse {
      return ImageResponse(
          id = image.id,
          bucket = image.bucket,
          serviceName = image.serviceName,
          storageService = image.storageService,
          status = image.status,
          uploaderId = image.uploaderId,
          storageType = image.storageType,
          fileName = image.fileName,
          thumbnailFileName = image.thumbnailFileName,
          url = image.url,
          thumbnailUrl = image.thumbnailUrl,
          width = image.width,
          height = image.height,
          fileSize = image.fileSize,
          thumbnailWidth = image.thumbnailWidth,
          thumbnailHeight = image.thumbnailHeight,
          thumbnailFileSize = image.thumbnailFileSize,
          createdAt = image.createdAt,
      )
    }
  }
}
