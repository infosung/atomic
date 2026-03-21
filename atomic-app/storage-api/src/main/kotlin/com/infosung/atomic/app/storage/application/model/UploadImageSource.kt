package com.infosung.atomic.app.storage.application.model

import java.io.File

/** Transport-agnostic upload source that can materialize into a temporary file. */
interface UploadImageSource {
  val originalFilename: String?

  fun transferTo(destinationFile: File)
}
