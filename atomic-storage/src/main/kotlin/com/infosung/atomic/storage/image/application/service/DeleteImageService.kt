package com.infosung.atomic.storage.image.application.service

import com.infosung.atomic.storage.image.application.support.ImageStorageAccessSupport

internal class DeleteImageService(
    private val imageStorageAccessSupport: ImageStorageAccessSupport,
) {
  fun deleteImage(
      storageType: String,
      fileName: String?,
      thumbnailFileName: String? = null,
  ) {
    val access = imageStorageAccessSupport.resolve(storageType)
    fileName
        ?.takeIf { it.isNotBlank() }
        ?.let { imageStorageAccessSupport.normalizeDeleteObjectKey(access, it) }
        ?.let(access.storageClient::deleteObject)
    thumbnailFileName
        ?.takeIf { it.isNotBlank() }
        ?.let { imageStorageAccessSupport.normalizeDeleteObjectKey(access, it) }
        ?.let(access.storageClient::deleteObject)
  }
}
