package com.infosung.atomic.app.storage.adapter.out.persistence

import com.infosung.atomic.app.storage.domain.StoredImage

object ImageEntityMapper {
  fun toDomain(entity: ImageEntity): StoredImage {
    return StoredImage(
        id = entity.id,
        bucket = entity.bucket,
        serviceName = entity.serviceName,
        storageService = entity.storageService,
        status = entity.status,
        uploaderId = entity.uploaderId,
        storageType = entity.storageType,
        fileName = entity.fileName,
        thumbnailFileName = entity.thumbnailFileName,
        url = entity.url,
        thumbnailUrl = entity.thumbnailUrl,
        width = entity.width,
        height = entity.height,
        fileSize = entity.fileSize,
        thumbnailWidth = entity.thumbnailWidth,
        thumbnailHeight = entity.thumbnailHeight,
        thumbnailFileSize = entity.thumbnailFileSize,
        createdAt = entity.createdAt,
    )
  }

  fun toEntity(image: StoredImage): ImageEntity {
    return ImageEntity(
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
