package com.infosung.atomic.storage.image.spi

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.storage.image.ImageStorageBudgets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.slf4j.LoggerFactory

/** Strategy for generating object keys for uploaded images. */
fun interface ImageObjectKeyGenerator {
  /**
   * Generates a storage object key for an uploaded file.
   *
   * @param originalFilename User-provided filename.
   * @return Object key relative to storage bucket/container.
   */
  fun generate(originalFilename: String): String
}

/**
 * Default key generator:
 * `basePrefix/yyyy/MM/dd/HH/{epochMillis}_{randomSuffix}_{sanitizedFilename}`.
 */
class DefaultImageObjectKeyGenerator(
    private val timeProvider: TimeProvider = TimeProvider(),
    private val basePrefix: String = "images",
    private val randomSuffixGenerator: () -> String = {
      UUID.randomUUID().toString().replace("-", "")
    },
) : ImageObjectKeyGenerator {
  private val datePathFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH")

  override fun generate(originalFilename: String): String {
    val now = LocalDateTime.ofInstant(timeProvider.nowInstant(), ZoneOffset.UTC)
    val datePath = now.format(datePathFormatter)
    val safeFilename = sanitizeFilename(originalFilename)
    val suffix = randomSuffixGenerator()
    val keyPrefix = "$basePrefix/$datePath/${timeProvider.nowMillis()}_${suffix}_"
    val maxFilenameLength = ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH - keyPrefix.length
    require(maxFilenameLength > 0) {
      "Generated object key prefix exceeds max length ${ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH}: prefixLength=${keyPrefix.length}"
    }

    val boundedFilename = boundFilenameComponent(safeFilename, maxFilenameLength)
    if (boundedFilename != safeFilename) {
      logger.debug(
          "Truncated sanitized filename for object key budget: originalLength={}, boundedLength={}, maxObjectKeyLength={}",
          safeFilename.length,
          boundedFilename.length,
          ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH,
      )
    }
    return "$keyPrefix$boundedFilename"
  }

  private fun sanitizeFilename(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    return base.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image" }
  }

  private fun boundFilenameComponent(
      fileName: String,
      maxLength: Int,
  ): String {
    if (fileName.length <= maxLength) {
      return fileName
    }

    val lastDot = fileName.lastIndexOf('.')
    val hasExtension = lastDot > 0 && lastDot < fileName.lastIndex
    if (!hasExtension) {
      return fileName.take(maxLength)
    }

    val extension = fileName.substring(lastDot)
    val baseName = fileName.substring(0, lastDot)
    if (extension.length >= maxLength) {
      return fileName.take(maxLength)
    }

    return baseName.take(maxLength - extension.length) + extension
  }

  companion object {
    private val logger = LoggerFactory.getLogger(DefaultImageObjectKeyGenerator::class.java)
  }
}
