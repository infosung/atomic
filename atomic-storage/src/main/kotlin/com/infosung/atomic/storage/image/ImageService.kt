package com.infosung.atomic.storage.image

import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.application.service.DeleteImageService
import com.infosung.atomic.storage.image.application.service.UploadImageService
import com.infosung.atomic.storage.image.application.support.ImageStorageAccessSupport
import java.io.File
import java.io.InputStream

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
  private val imageStorageAccessSupport: ImageStorageAccessSupport =
      ImageStorageAccessSupport(
          storageClients = storageClients,
          storageProfiles = storageProfiles,
      )
  private val uploadImageService: UploadImageService =
      UploadImageService(
          imageStorageAccessSupport = imageStorageAccessSupport,
          objectKeyGenerator = objectKeyGenerator,
          imageInputValidator = imageInputValidator,
          metadataReader = metadataReader,
          thumbnailGenerator = thumbnailGenerator,
      )
  private val deleteImageService: DeleteImageService =
      DeleteImageService(imageStorageAccessSupport = imageStorageAccessSupport)

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
    return uploadImageService.uploadImage(
        file = file,
        originFilename = originFilename,
        storageType = storageType,
        quality = quality,
        generateThumbnail = generateThumbnail,
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
    return uploadImageService.uploadImage(
        inputStream = inputStream,
        originFilename = originFilename,
        storageType = storageType,
        quality = quality,
        generateThumbnail = generateThumbnail,
    )
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
    deleteImageService.deleteImage(
        storageType = storageType,
        fileName = fileName,
        thumbnailFileName = thumbnailFileName,
    )
  }
}
