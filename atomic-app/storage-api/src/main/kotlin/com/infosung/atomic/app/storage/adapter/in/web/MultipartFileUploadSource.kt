package com.infosung.atomic.app.storage.adapter.`in`.web

import com.infosung.atomic.app.storage.application.model.UploadImageSource
import java.io.File
import org.springframework.web.multipart.MultipartFile

class MultipartFileUploadSource(
    private val multipartFile: MultipartFile,
) : UploadImageSource {
  override val originalFilename: String?
    get() = multipartFile.originalFilename

  override fun transferTo(destinationFile: File) {
    multipartFile.transferTo(destinationFile)
  }
}
