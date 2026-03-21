package com.infosung.atomic.storage.image.application.service

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.image.ImageFileInfo
import com.infosung.atomic.storage.image.ImageInputValidator
import com.infosung.atomic.storage.image.ImageMetadataReader
import com.infosung.atomic.storage.image.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.ImageStorageBudgets
import com.infosung.atomic.storage.image.ImageThumbnailGenerator
import com.infosung.atomic.storage.image.ImageUploadResult
import com.infosung.atomic.storage.image.application.support.ImageStorageAccessSupport
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import org.slf4j.LoggerFactory

internal class UploadImageService(
    private val imageStorageAccessSupport: ImageStorageAccessSupport,
    private val objectKeyGenerator: ImageObjectKeyGenerator,
    private val imageInputValidator: ImageInputValidator,
    private val metadataReader: ImageMetadataReader,
    private val thumbnailGenerator: ImageThumbnailGenerator,
) {
  fun uploadImage(
      file: File,
      originFilename: String,
      storageType: String,
      quality: Double = 1.0,
      generateThumbnail: Boolean,
  ): ImageUploadResult {
    require(file.exists() && file.isFile) { "file must exist and be a file." }
    require(quality in 0.1..1.0) { "quality must be in range 0.1..1.0" }

    val access = imageStorageAccessSupport.resolve(storageType)
    val validatedImage = imageInputValidator.validate(file, originFilename)
    val objectKey = objectKeyGenerator.generate(originFilename)
    ensureOriginalObjectKeyWithinBudget(objectKey)
    val storedObjectKey = imageStorageAccessSupport.toStoredObjectKey(access, objectKey)
    val originalUrl = buildValidatedPublicUrl(access, objectKey)
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

    access.storageClient.putObject(
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
          val resolvedStoredThumbnailObjectKey =
              imageStorageAccessSupport.toStoredObjectKey(access, generated.objectKey)
          val resolvedThumbnailUrl = buildValidatedPublicUrl(access, generated.objectKey)
          access.storageClient.putObject(
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
        bucket = access.storageProfile.bucket,
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

  private fun buildValidatedPublicUrl(
      access: com.infosung.atomic.storage.image.domain.ResolvedImageStorageAccess,
      objectKey: String,
  ): String {
    val url = imageStorageAccessSupport.toPublicUrl(access, objectKey)
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

  private fun summarizeThumbnailFailureReason(error: Throwable): String {
    val base = "${error::class.simpleName}: ${error.message ?: "thumbnail generation failed"}"
    return ImageStorageBudgets.summarizeFailureReason(base)
  }

  private fun summarizeForLog(value: String?): String? = ImageStorageBudgets.summarizeForLog(value)

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
    private val logger = LoggerFactory.getLogger(UploadImageService::class.java)
  }
}
