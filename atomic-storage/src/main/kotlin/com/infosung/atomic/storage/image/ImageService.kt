package com.infosung.atomic.storage.image

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import org.slf4j.LoggerFactory

/**
 * High-level image upload/delete service.
 *
 * This service validates input images, uploads the original image, tries to generate/upload
 * thumbnail, and returns URL and metadata payloads for callers.
 */
class ImageService(
    private val storageClients: Map<String, StorageClient>,
    private val storageProfiles: Map<String, StorageProfile>,
    private val objectKeyGenerator: ImageObjectKeyGenerator = DefaultImageObjectKeyGenerator(),
    private val imageInputValidator: ImageInputValidator = CommonsImagingImageInputValidator(),
    private val metadataReader: ImageMetadataReader = CommonsImagingMetadataReader(),
    private val thumbnailGenerator: ImageThumbnailGenerator = DefaultImageThumbnailGenerator(),
) {
  /**
   * Uploads an image file and optionally a thumbnail.
   *
   * Expected behavior:
   * - Original upload success means overall request success.
   * - Thumbnail generation/upload failures are captured in result fields and do not fail call.
   * - Interrupt-related exceptions are rethrown and thread interrupted state is restored.
   *
   * @param file Source image file.
   * @param originFilename Original user filename used for extension validation and key generation.
   * @param storageType Logical storage profile key (for example `S3`, `R2`).
   * @param quality Thumbnail quality/scale in range `0.1..1.0`.
   * @return Upload result containing object keys, URLs, and metadata.
   * @throws IllegalArgumentException If file/quality/storageType/profile/input format is invalid.
   * @throws InterruptedException or interruption-like runtime exceptions for cancellation
   *   scenarios.
   */
  fun uploadImage(
      file: File,
      originFilename: String,
      storageType: String,
      quality: Double = 1.0,
  ): ImageUploadResult {
    return uploadImage(
        file = file,
        originFilename = originFilename,
        storageType = storageType,
        quality = quality,
        generateThumbnail = true,
    )
  }

  fun uploadImage(
      file: File,
      originFilename: String,
      storageType: String,
      quality: Double = 1.0,
      generateThumbnail: Boolean,
  ): ImageUploadResult {
    require(file.exists() && file.isFile) { "file must exist and be a file." }
    require(quality in 0.1..1.0) { "quality must be in range 0.1..1.0" }

    val client = resolveStorageClient(storageType)
    val profile = resolveStorageProfile(storageType)
    val validatedImage = imageInputValidator.validate(file, originFilename)
    val objectKey = objectKeyGenerator.generate(originFilename)
    ensureOriginalObjectKeyWithinBudget(objectKey)
    val bucketPrefix = if (profile.prependBucketOnObjectKey) "${profile.bucket}/" else ""
    val storedObjectKey = "$bucketPrefix$objectKey"
    val originalUrl = buildValidatedPublicUrl(profile.cdn, storedObjectKey)
    val originalMetadata = metadataReader.read(file)
    logger.debug(
        "Uploading image object: storageType={}, originFilenamePreview={}, originFilenameLength={}, objectKeyPreview={}, objectKeyLength={}, generateThumbnail={}, quality={}",
        storageType,
        summarizeForLog(originFilename),
        originFilename.length,
        summarizeForLog(objectKey),
        objectKey.length,
        generateThumbnail,
        quality,
    )

    client.putObject(
        PutObjectRequest(
            objectKey = objectKey,
            file = file,
            contentType = validatedImage.contentType,
            metadata =
                mapOf(
                    "width" to originalMetadata.width.toString(),
                    "height" to originalMetadata.height.toString(),
                    "size" to originalMetadata.size.toString(),
                ),
            contentLength = originalMetadata.size,
        ),
    )

    var thumbnailInfo = ImageFileInfo()
    var thumbnailFailed = false
    var thumbnailFailureReason: String? = null
    var thumbnailUrl: String? = null
    var storedThumbnailObjectKey: String? = null
    if (generateThumbnail) {
      try {
        val generated =
            thumbnailGenerator.generate(
                sourceFile = file,
                sourceFilename = "source.${validatedImage.extension}",
                sourceObjectKey = objectKey,
                quality = quality,
            )
        try {
          ensureThumbnailObjectKeyWithinBudget(generated.objectKey)
          val resolvedStoredThumbnailObjectKey = "$bucketPrefix${generated.objectKey}"
          val resolvedThumbnailUrl =
              buildValidatedPublicUrl(profile.cdn, resolvedStoredThumbnailObjectKey)
          client.putObject(
              PutObjectRequest(
                  objectKey = generated.objectKey,
                  file = generated.file,
                  contentType = "image/webp",
                  metadata =
                      mapOf(
                          "width" to generated.metadata.width.toString(),
                          "height" to generated.metadata.height.toString(),
                          "size" to generated.metadata.size.toString(),
                      ),
                  contentLength = generated.metadata.size,
              ),
          )
          storedThumbnailObjectKey = resolvedStoredThumbnailObjectKey
          thumbnailUrl = resolvedThumbnailUrl
          thumbnailInfo =
              ImageFileInfo(
                  fileName = generated.objectKey,
                  width = generated.metadata.width,
                  height = generated.metadata.height,
                  size = generated.metadata.size,
              )
          logger.debug(
              "Thumbnail upload completed: storageType={}, objectKeyPreview={}, objectKeyLength={}, thumbnailObjectKeyPreview={}, thumbnailObjectKeyLength={}",
              storageType,
              summarizeForLog(objectKey),
              objectKey.length,
              summarizeForLog(generated.objectKey),
              generated.objectKey.length,
          )
        } finally {
          deleteTempFile(generated.file, "generated thumbnail")
        }
      } catch (e: Exception) {
        if (isInterruptedException(e)) {
          Thread.currentThread().interrupt()
          throw e
        }
        thumbnailFailed = true
        thumbnailFailureReason = summarizeThumbnailFailureReason(e)
        logger.warn(
            "Thumbnail upload failed but original image upload remains successful: storageType={}, objectKeyPreview={}, objectKeyLength={}, reason={}, reasonLength={}",
            storageType,
            summarizeForLog(objectKey),
            objectKey.length,
            thumbnailFailureReason,
            thumbnailFailureReason.length,
        )
      }
    } else {
      logger.info(
          "Thumbnail generation skipped by configuration/request: storageType={}, objectKeyPreview={}, objectKeyLength={}, originFilenamePreview={}, originFilenameLength={}",
          storageType,
          summarizeForLog(objectKey),
          objectKey.length,
          summarizeForLog(originFilename),
          originFilename.length,
      )
    }

    return ImageUploadResult(
        storageType = storageType,
        bucket = profile.bucket,
        storageObjectKey = objectKey,
        storageThumbnailObjectKey = thumbnailInfo.fileName,
        fileName = storedObjectKey,
        thumbnailFileName = storedThumbnailObjectKey,
        url = originalUrl,
        thumbnailUrl = thumbnailUrl,
        width = originalMetadata.width,
        height = originalMetadata.height,
        fileSize = originalMetadata.size,
        thumbnailWidth = thumbnailInfo.width,
        thumbnailHeight = thumbnailInfo.height,
        thumbnailFileSize = thumbnailInfo.size,
        thumbnailUploadFailed = thumbnailFailed,
        thumbnailFailureReason = thumbnailFailureReason,
    )
  }

  /**
   * Uploads from an input stream by materializing it into a temporary file.
   *
   * Notes:
   * - The stream is consumed but not closed by this method.
   * - Caller/application layer should enforce upload size limits (for example Spring multipart).
   *
   * @param inputStream Source stream to consume.
   * @param originFilename Original user filename used for extension validation and key generation.
   * @param storageType Logical storage profile key.
   * @param quality Thumbnail quality/scale in range `0.1..1.0`.
   * @return Upload result containing object keys, URLs, and metadata.
   * @throws IllegalArgumentException If storageType/profile/quality/input format is invalid.
   * @throws InterruptedException or interruption-like runtime exceptions for cancellation
   *   scenarios.
   */
  fun uploadImage(
      inputStream: InputStream,
      originFilename: String,
      storageType: String,
      quality: Double = 1.0,
  ): ImageUploadResult {
    return uploadImage(
        inputStream = inputStream,
        originFilename = originFilename,
        storageType = storageType,
        quality = quality,
        generateThumbnail = true,
    )
  }

  fun uploadImage(
      inputStream: InputStream,
      originFilename: String,
      storageType: String,
      quality: Double = 1.0,
      generateThumbnail: Boolean,
  ): ImageUploadResult {
    val extension = originFilename.substringAfterLast('.', "").ifBlank { "tmp" }
    val tempFile = File.createTempFile("atomic-storage-upload-", ".$extension")
    try {
      // The caller should enforce max upload size (for example via Spring multipart limits).
      tempFile.outputStream().use { output -> inputStream.copyTo(output) }
      return uploadImage(
          file = tempFile,
          originFilename = originFilename,
          storageType = storageType,
          quality = quality,
          generateThumbnail = generateThumbnail,
      )
    } finally {
      deleteTempFile(tempFile, "image upload input stream")
    }
  }

  /**
   * Deletes original and thumbnail objects if keys are present.
   *
   * @param storageType Logical storage profile key.
   * @param fileName Original object key (or display key when bucket prefix is enabled).
   * @param thumbnailFileName Thumbnail object key (or display key when bucket prefix is enabled).
   * @throws IllegalArgumentException If storageType/profile is unknown.
   */
  fun deleteImage(
      storageType: String,
      fileName: String?,
      thumbnailFileName: String? = null,
  ) {
    val client = resolveStorageClient(storageType)
    val profile = resolveStorageProfile(storageType)
    fileName
        ?.takeIf { it.isNotBlank() }
        ?.let { normalizeStorageObjectKey(profile, it) }
        ?.let(client::deleteObject)
    thumbnailFileName
        ?.takeIf { it.isNotBlank() }
        ?.let { normalizeStorageObjectKey(profile, it) }
        ?.let(client::deleteObject)
  }

  private fun resolveStorageClient(storageType: String): StorageClient {
    return storageClients[storageType]
        ?: throw IllegalArgumentException("Unknown storageType: $storageType")
  }

  private fun resolveStorageProfile(storageType: String): StorageProfile {
    return storageProfiles[storageType]
        ?: throw IllegalArgumentException("Unknown storageType profile: $storageType")
  }

  private fun normalizeStorageObjectKey(
      profile: StorageProfile,
      objectKey: String,
  ): String {
    if (!profile.prependBucketOnObjectKey) return objectKey
    val prefix = "${profile.bucket}/"
    return if (objectKey.startsWith(prefix)) objectKey.removePrefix(prefix) else objectKey
  }

  private fun isInterruptedException(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
      if (current is InterruptedException || current is InterruptedIOException) return true
      if (current::class.java.name ==
          "software.amazon.awssdk.core.exception.SdkInterruptedException") {
        return true
      }
      current = current.cause
    }
    return false
  }

  private fun buildCdnUrl(
      cdn: String,
      objectKey: String,
  ): String = "${cdn.trimEnd('/')}/$objectKey"

  private fun ensureOriginalObjectKeyWithinBudget(objectKey: String) {
    require(objectKey.length <= ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH) {
      "Generated object key exceeds max length ${ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH}: length=${objectKey.length}"
    }
    logger.debug(
        "Validated original object key budget: objectKeyPreview={}, objectKeyLength={}, maxObjectKeyLength={}",
        summarizeForLog(objectKey),
        objectKey.length,
        ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH,
    )
  }

  private fun ensureThumbnailObjectKeyWithinBudget(objectKey: String) {
    require(objectKey.length <= ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH) {
      "Generated thumbnail object key exceeds max length ${ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH}: length=${objectKey.length}"
    }
    logger.debug(
        "Validated thumbnail object key budget: objectKeyPreview={}, objectKeyLength={}, maxObjectKeyLength={}",
        summarizeForLog(objectKey),
        objectKey.length,
        ImageStorageBudgets.MAX_OBJECT_KEY_LENGTH,
    )
  }

  private fun buildValidatedPublicUrl(
      cdn: String,
      objectKey: String,
  ): String {
    val url = buildCdnUrl(cdn, objectKey)
    require(url.length <= ImageStorageBudgets.MAX_PUBLIC_URL_LENGTH) {
      "Generated public url exceeds max length ${ImageStorageBudgets.MAX_PUBLIC_URL_LENGTH}: length=${url.length}"
    }
    logger.debug(
        "Validated public url budget: objectKeyPreview={}, objectKeyLength={}, urlLength={}, maxPublicUrlLength={}",
        summarizeForLog(objectKey),
        objectKey.length,
        url.length,
        ImageStorageBudgets.MAX_PUBLIC_URL_LENGTH,
    )
    return url
  }

  private fun summarizeThumbnailFailureReason(error: Throwable): String {
    val base = "${error::class.simpleName}: ${error.message ?: "thumbnail generation failed"}"
    return ImageStorageBudgets.summarizeFailureReason(base)
  }

  private fun summarizeForLog(value: String?): String? = ImageStorageBudgets.summarizeForLog(value)

  private fun deleteTempFile(
      file: File,
      context: String,
  ) {
    if (!file.exists()) return
    if (!file.delete()) {
      file.deleteOnExit()
      logger.warn(
          "Failed to delete temporary file ($context): ${file.name}. Scheduled deleteOnExit().",
      )
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(ImageService::class.java)
  }
}
