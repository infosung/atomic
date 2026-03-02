package com.infosung.atomic.storage.image

import com.infosung.atomic.contract.time.TimeProvider
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Strategy for generating object keys for uploaded images.
 */
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
    return "$basePrefix/$datePath/${timeProvider.nowMillis()}_${suffix}_$safeFilename"
  }

  private fun sanitizeFilename(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    return base.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image" }
  }
}
