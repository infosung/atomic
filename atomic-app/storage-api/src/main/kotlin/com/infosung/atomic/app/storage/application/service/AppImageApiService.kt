package com.infosung.atomic.app.storage.application.service

import com.infosung.atomic.app.storage.application.exception.ImageOwnershipMismatchException
import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.model.AppImageRequestPolicy
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.application.port.out.ImageObjectStoragePort
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.contract.log.LogStringPreview
import java.util.UUID
import org.slf4j.LoggerFactory

/** Upload/delete service for common image API. */
class AppImageApiService(
    private val imageMetadataPort: ImageMetadataPort,
    private val imageObjectStoragePort: ImageObjectStoragePort,
    private val requestPolicy: AppImageRequestPolicy,
) : UploadAppImageUseCase, DeleteAppImageUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun uploadImage(command: UploadAppImageCommand): StoredImage {
    if (command.quality !in requestPolicy.minQuality..requestPolicy.maxQuality) {
      throw InvalidImageRequestException(
          "quality must be in range ${requestPolicy.minQuality}..${requestPolicy.maxQuality}",
      )
    }

    val originalFilename =
        command.uploadSource.originalFilename?.takeIf { it.isNotBlank() }
            ?: throw InvalidImageRequestException("file original filename is required.")
    val resolvedUploaderId = resolveUploaderIdForUpload(command.uploaderId)
    log.debug(
        "Uploading image: serviceName={}, storageService={}, originalFilenamePreview={}, originalFilenameLength={}, uploaderTracked={}, thumbnailEnabled={}",
        command.serviceName,
        command.storageService,
        LogStringPreview.summarize(originalFilename),
        originalFilename.length,
        resolvedUploaderId != null,
        command.thumbnailEnabled,
    )

    val uploaded =
        imageObjectStoragePort.uploadImage(
            serviceName = command.serviceName,
            storageService = command.storageService,
            uploadSource = command.uploadSource,
            quality = command.quality,
            thumbnailEnabled = command.thumbnailEnabled,
        )
    val image =
        StoredImage(
            bucket = uploaded.bucket,
            serviceName = command.serviceName,
            storageService = command.storageService,
            status = StoredImage.STATUS_ACTIVE,
            uploaderId = resolvedUploaderId,
            storageType = uploaded.storageType,
            fileName = uploaded.fileName,
            thumbnailFileName = uploaded.thumbnailFileName,
            url = uploaded.url,
            thumbnailUrl = uploaded.thumbnailUrl,
            width = uploaded.width,
            height = uploaded.height,
            fileSize = uploaded.fileSize,
            thumbnailWidth = uploaded.thumbnailWidth,
            thumbnailHeight = uploaded.thumbnailHeight,
            thumbnailFileSize = uploaded.thumbnailFileSize,
        )

    return try {
      val saved = imageMetadataPort.save(image)
      log.info(
          "Image metadata saved: serviceName={}, storageService={}, imageId={}, objectKeyPreview={}, objectKeyLength={}, urlLength={}, thumbnailUrlLength={}",
          command.serviceName,
          command.storageService,
          saved.id,
          LogStringPreview.summarize(saved.fileName),
          saved.fileName?.length ?: 0,
          saved.url.length,
          saved.thumbnailUrl?.length ?: 0,
      )
      saved
    } catch (e: Exception) {
      log.error(
          "Image metadata save failed after storage upload. Trying compensating delete: serviceName={}, storageService={}, storageType={}, objectKeyPreview={}",
          command.serviceName,
          command.storageService,
          uploaded.storageType,
          LogStringPreview.summarize(uploaded.fileName),
          e,
      )
      runCatching {
            imageObjectStoragePort.deleteUploadedImageObject(
                storageType = uploaded.storageType,
                fileName = uploaded.fileName,
                thumbnailFileName = uploaded.thumbnailFileName,
            )
          }
          .onFailure { cleanupError ->
            log.error(
                "Compensating storage delete failed: serviceName={}, storageService={}, storageType={}, objectKeyPreview={}",
                command.serviceName,
                command.storageService,
                uploaded.storageType,
                LogStringPreview.summarize(uploaded.fileName),
                cleanupError,
            )
          }
      throw e
    }
  }

  override fun deleteImage(command: DeleteAppImageCommand) {
    log.debug(
        "Deleting image: imageId={}, serviceName={}, storageService={}, uploaderTracked={}",
        command.imageId,
        command.serviceName,
        command.storageService,
        requestPolicy.uploaderParameterEnabled,
    )
    val imageUuid =
        runCatching { UUID.fromString(command.imageId) }
            .getOrElse { throw InvalidImageRequestException("imageId must be a valid UUID.", it) }
    val image = imageMetadataPort.findByIdOrThrow(imageUuid, command.imageId)

    if (!image.serviceName.equals(command.serviceName, ignoreCase = true) ||
        !image.storageService.equals(command.storageService, ignoreCase = true)) {
      log.warn(
          "Delete rejected due to service/storage mismatch: imageId={}, requestedService={}, requestedStorage={}, storedService={}, storedStorage={}",
          command.imageId,
          command.serviceName,
          command.storageService,
          image.serviceName,
          image.storageService,
      )
      throw InvalidImageRequestException("image does not match service/storage path parameters.")
    }
    validateDeleteUploader(image = image, uploaderId = command.uploaderId)

    if (image.status == StoredImage.STATUS_DELETE_PENDING) {
      log.info(
          "Delete requested for image metadata already marked delete-pending. Retrying storage cleanup: imageId={}, serviceName={}, storageService={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}",
          command.imageId,
          command.serviceName,
          command.storageService,
          image.storageType,
          LogStringPreview.summarize(image.fileName),
          LogStringPreview.summarize(image.thumbnailFileName),
      )
    }
    val deletePendingImage = imageMetadataPort.markDeletePending(image)

    try {
      imageObjectStoragePort.deleteImage(
          imageId = command.imageId,
          serviceName = command.serviceName,
          storageService = command.storageService,
          storedImage = deletePendingImage,
      )
    } catch (e: Exception) {
      log.error(
          "Storage delete failed while metadata remains delete-pending. Retry delete or run recovery use-case: imageId={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}",
          command.imageId,
          deletePendingImage.storageType,
          LogStringPreview.summarize(deletePendingImage.fileName),
          LogStringPreview.summarize(deletePendingImage.thumbnailFileName),
          e,
      )
      throw e
    }

    try {
      imageMetadataPort.purgeDeletePending(deletePendingImage)
    } catch (e: Exception) {
      log.error(
          "Image metadata purge failed after storage delete: imageId={}, storageType={}, fileNamePreview={}, status={}",
          command.imageId,
          deletePendingImage.storageType,
          LogStringPreview.summarize(deletePendingImage.fileName),
          deletePendingImage.status,
          e,
      )
      throw e
    }
    log.info(
        "Image metadata deleted: imageId={}, serviceName={}, storageService={}, uploaderTracked={}",
        command.imageId,
        command.serviceName,
        command.storageService,
        deletePendingImage.uploaderId != null,
    )
  }

  private fun resolveUploaderIdForUpload(uploaderId: String?): String? {
    if (!requestPolicy.uploaderParameterEnabled) {
      return null
    }
    val parameterName = resolveUploaderParameterName()
    return uploaderId?.takeIf { it.isNotBlank() }
        ?: throw InvalidImageRequestException(
            "$parameterName is required when uploader parameter tracking is enabled.",
        )
  }

  private fun validateDeleteUploader(
      image: StoredImage,
      uploaderId: String?,
  ) {
    if (!requestPolicy.uploaderParameterEnabled) {
      return
    }
    val parameterName = resolveUploaderParameterName()
    val requestUploaderId =
        uploaderId?.takeIf { it.isNotBlank() }
            ?: throw InvalidImageRequestException(
                "$parameterName is required when uploader parameter tracking is enabled.",
            )
    if (image.uploaderId != requestUploaderId) {
      log.warn(
          "Delete rejected due to uploader mismatch: imageId={}, requestUploaderId={}, storedUploaderId={}",
          image.id,
          requestUploaderId,
          image.uploaderId,
      )
      throw ImageOwnershipMismatchException(
          "uploader parameter does not match uploaded image owner.",
      )
    }
  }

  private fun resolveUploaderParameterName(): String {
    val parameterName = requestPolicy.uploaderParameterName.trim()
    if (parameterName.isBlank()) {
      throw IllegalStateException(
          "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
      )
    }
    return parameterName
  }
}
