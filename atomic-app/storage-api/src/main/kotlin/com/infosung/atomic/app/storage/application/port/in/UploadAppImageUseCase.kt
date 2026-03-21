package com.infosung.atomic.app.storage.application.port.`in`

import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.domain.StoredImage

fun interface UploadAppImageUseCase {
  fun uploadImage(command: UploadAppImageCommand): StoredImage
}
