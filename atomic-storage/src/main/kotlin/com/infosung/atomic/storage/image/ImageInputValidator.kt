package com.infosung.atomic.storage.image

import java.io.File
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging

/**
 * Validated image type information derived from filename and file contents.
 *
 * @property extension Normalized extension (lowercase).
 * @property contentType MIME type for upload.
 * @property detectedFormat Format name detected from file contents.
 */
data class ValidatedImageInput(
    val extension: String,
    val contentType: String,
    val detectedFormat: String,
)

/**
 * Validates image uploads before processing/storage.
 */
fun interface ImageInputValidator {
  /**
   * Validates extension and file content.
   *
   * @param file Uploaded local file.
   * @param originalFilename Original filename used for extension checks.
   * @return Validated image information used by upload pipeline.
   * @throws IllegalArgumentException If extension is unsupported/missing or mismatched with content.
   */
  fun validate(
      file: File,
      originalFilename: String,
  ): ValidatedImageInput
}

/**
 * [ImageInputValidator] implementation backed by Apache Commons Imaging.
 */
class CommonsImagingImageInputValidator(
    private val allowedExtensions: Set<String> = DEFAULT_ALLOWED_EXTENSIONS,
) : ImageInputValidator {
  override fun validate(
      file: File,
      originalFilename: String,
  ): ValidatedImageInput {
    val extension = normalizeExtension(originalFilename)
    if (extension !in allowedExtensions) {
      throw IllegalArgumentException("Unsupported image extension: $extension")
    }

    val guessedFormat = Imaging.guessFormat(file)
    if (guessedFormat == ImageFormats.UNKNOWN) {
      throw IllegalArgumentException("Unable to detect image format from file contents.")
    }

    val expectedFormat = formatByExtension(extension)
    if (guessedFormat != expectedFormat) {
      throw IllegalArgumentException(
          "Image extension does not match detected format: extension=$extension, detected=${guessedFormat.getName()}",
      )
    }

    return ValidatedImageInput(
        extension = extension,
        contentType = contentTypeByExtension(extension),
        detectedFormat = guessedFormat.name,
    )
  }

  private fun normalizeExtension(filename: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) {
      throw IllegalArgumentException("Image filename must include an extension.")
    }
    return extension
  }

  private fun formatByExtension(extension: String): ImageFormats {
    return when (extension) {
      "jpg",
      "jpeg" -> ImageFormats.JPEG
      "png" -> ImageFormats.PNG
      "webp" -> ImageFormats.WEBP
      "gif" -> ImageFormats.GIF
      "bmp" -> ImageFormats.BMP
      else -> throw IllegalArgumentException("Unsupported image extension: $extension")
    }
  }

  private fun contentTypeByExtension(extension: String): String {
    return when (extension) {
      "jpg",
      "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      "gif" -> "image/gif"
      "bmp" -> "image/bmp"
      else -> "application/octet-stream"
    }
  }

  companion object {
    val DEFAULT_ALLOWED_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
  }
}
