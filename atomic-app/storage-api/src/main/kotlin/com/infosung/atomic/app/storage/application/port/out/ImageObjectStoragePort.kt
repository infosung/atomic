package com.infosung.atomic.app.storage.application.port.out

import com.infosung.atomic.app.storage.application.model.StoredImageObject
import com.infosung.atomic.app.storage.application.model.UploadImageSource
import com.infosung.atomic.app.storage.domain.StoredImage

interface ImageObjectStoragePort {
  fun uploadImage(
      serviceName: String,
      storageService: String,
      uploadSource: UploadImageSource,
      quality: Double,
      thumbnailEnabled: Boolean,
  ): StoredImageObject

  fun deleteImage(
      imageId: String,
      serviceName: String,
      storageService: String,
      storedImage: StoredImage,
  )

  fun deleteUploadedImageObject(
      storageType: String,
      fileName: String?,
      thumbnailFileName: String?,
  )
}
