package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.image.ImageService
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.web.multipart.MultipartFile

/** Upload/delete service for common image API. */
class AppImageApiService(
    private val imageEntityTxService: AppImageEntityTxService,
    private val imageService: ImageService,
    private val storageClients: Map<String, StorageClient>,
    private val properties: AtomicAppImageProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Uploads image to storage and saves image metadata row.
   *
   * When `atomic.app.image.uploader-parameter-enabled=true`, `uploaderId` is required and
   * persisted.
   */
  fun uploadImage(
      serviceName: String,
      storageService: String,
      multipartFile: MultipartFile,
      quality: Double,
      uploaderId: String? = null,
  ): ImageEntity {
    return uploadImage(
        serviceName = serviceName,
        storageService = storageService,
        multipartFile = multipartFile,
        quality = quality,
        uploaderId = uploaderId,
        thumbnailEnabled = properties.thumbnailEnabled,
    )
  }

  fun uploadImage(
      serviceName: String,
      storageService: String,
      multipartFile: MultipartFile,
      quality: Double,
      uploaderId: String? = null,
      thumbnailEnabled: Boolean,
  ): ImageEntity {
    if (quality !in properties.minQuality..properties.maxQuality) {
      throw HttpStatusException(
          status = 400,
          message = "quality must be in range ${properties.minQuality}..${properties.maxQuality}",
      )
    }

    val originalFilename =
        multipartFile.originalFilename?.takeIf { it.isNotBlank() }
            ?: throw HttpStatusException(
                status = 400, message = "file original filename is required.")

    val resolvedStorageType =
        resolveStorageType(serviceName = serviceName, storageService = storageService)
    val resolvedUploaderId = resolveUploaderIdForUpload(uploaderId)
    log.debug(
        "Uploading image: serviceName={}, storageService={}, resolvedStorageType={}, originalFilename={}, uploaderTracked={}, thumbnailEnabled={}",
        serviceName,
        storageService,
        resolvedStorageType,
        originalFilename,
        resolvedUploaderId != null,
        thumbnailEnabled,
    )

    val extension = originalFilename.substringAfterLast('.', "tmp")
    val tempFile = kotlin.io.path.createTempFile("atomic-app-image-", ".$extension").toFile()
    try {
      multipartFile.transferTo(tempFile)
      val uploaded =
          imageService.uploadImage(
              file = tempFile,
              originFilename = originalFilename,
              storageType = resolvedStorageType,
              quality = quality,
              generateThumbnail = thumbnailEnabled,
          )
      log.debug(
          "Storage upload completed: serviceName={}, storageService={}, objectKey={}, thumbnailKey={}, thumbnailEnabled={}",
          serviceName,
          storageService,
          uploaded.fileName,
          uploaded.thumbnailFileName,
          thumbnailEnabled,
      )
      val entity =
          ImageEntity(
              bucket = uploaded.bucket,
              serviceName = serviceName,
              storageService = storageService,
              status = ImageEntity.STATUS_ACTIVE,
              uploaderId = resolvedUploaderId,
              storageType = resolvedStorageType,
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
        val saved = imageEntityTxService.save(entity)
        log.info(
            "Image metadata saved: serviceName={}, storageService={}, imageId={}, objectKey={}",
            serviceName,
            storageService,
            saved.id,
            saved.fileName,
        )
        saved
      } catch (e: Exception) {
        // Storage upload succeeded but metadata save failed. Try compensating cleanup.
        log.error(
            "Image metadata save failed after storage upload. Trying compensating delete: serviceName={}, " +
                "storageService={}, storageType={}, objectKey={}",
            serviceName,
            storageService,
            resolvedStorageType,
            uploaded.fileName,
            e,
        )
        runCatching {
              imageService.deleteImage(
                  storageType = resolvedStorageType,
                  fileName = uploaded.fileName,
                  thumbnailFileName = uploaded.thumbnailFileName,
              )
            }
            .onFailure { cleanupError ->
              log.error(
                  "Compensating storage delete failed: serviceName={}, storageService={}, storageType={}, objectKey={}",
                  serviceName,
                  storageService,
                  resolvedStorageType,
                  uploaded.fileName,
                  cleanupError,
              )
            }
        throw e
      }
    } finally {
      tempFile.delete()
    }
  }

  /**
   * Deletes image object(s) and metadata row.
   *
   * When `atomic.app.image.uploader-parameter-enabled=true`, `uploaderId` is required and must
   * match `ImageEntity.uploaderId`.
   */
  fun deleteImage(
      serviceName: String,
      storageService: String,
      imageId: String,
      uploaderId: String? = null,
  ) {
    log.debug(
        "Deleting image: imageId={}, serviceName={}, storageService={}, uploaderTracked={}",
        imageId,
        serviceName,
        storageService,
        properties.uploaderParameterEnabled,
    )
    val uuid =
        runCatching { UUID.fromString(imageId) }
            .getOrElse {
              throw HttpStatusException(
                  status = 400, message = "imageId must be a valid UUID.", cause = it)
            }

    val imageEntity =
        try {
          imageEntityTxService.findByIdOrThrow(uuid, imageId)
        } catch (_: IllegalArgumentException) {
          throw HttpStatusException(status = 404, message = "image not found: $imageId")
        }

    if (!imageEntity.serviceName.equals(serviceName, ignoreCase = true) ||
        !imageEntity.storageService.equals(storageService, ignoreCase = true)) {
      log.warn(
          "Delete rejected due to service/storage mismatch: imageId={}, requestedService={}, requestedStorage={}, " +
              "storedService={}, storedStorage={}",
          imageId,
          serviceName,
          storageService,
          imageEntity.serviceName,
          imageEntity.storageService,
      )
      throw HttpStatusException(
          status = 400,
          message = "image does not match service/storage path parameters.",
      )
    }
    validateDeleteUploader(imageEntity = imageEntity, uploaderId = uploaderId)

    val resolvedStorageType =
        resolveStoredStorageTypeForDelete(
            imageId = imageId,
            imageEntity = imageEntity,
            serviceName = serviceName,
            storageService = storageService,
        )
    val deleteReservedEntity = imageEntityTxService.markDeletePending(imageEntity)

    try {
      imageService.deleteImage(
          storageType = resolvedStorageType,
          fileName = deleteReservedEntity.fileName,
          thumbnailFileName = deleteReservedEntity.thumbnailFileName,
      )
      log.info(
          "Storage objects deleted for image delete workflow: imageId={}, storageType={}, fileName={}, thumbnailFileName={}",
          imageId,
          resolvedStorageType,
          deleteReservedEntity.fileName,
          deleteReservedEntity.thumbnailFileName,
      )
    } catch (e: Exception) {
      log.error(
          "Storage delete failed while metadata remains delete-pending: imageId={}, storageType={}, fileName={}, thumbnailFileName={}",
          imageId,
          resolvedStorageType,
          deleteReservedEntity.fileName,
          deleteReservedEntity.thumbnailFileName,
          e,
      )
      throw e
    }
    try {
      imageEntityTxService.purgeDeletePending(deleteReservedEntity)
    } catch (e: Exception) {
      log.error(
          "Image metadata purge failed after storage delete: imageId={}, storageType={}, fileName={}, status={}",
          imageId,
          resolvedStorageType,
          deleteReservedEntity.fileName,
          deleteReservedEntity.status,
          e,
      )
      throw e
    }
    log.info(
        "Image metadata deleted: imageId={}, serviceName={}, storageService={}, uploaderTracked={}",
        imageId,
        serviceName,
        storageService,
        deleteReservedEntity.uploaderId != null,
    )
  }

  private fun resolveUploaderIdForUpload(uploaderId: String?): String? {
    if (!properties.uploaderParameterEnabled) {
      return null
    }
    val parameterName = resolveUploaderParameterName()
    return uploaderId?.takeIf { it.isNotBlank() }
        ?: throw HttpStatusException(
            status = 400,
            message = "$parameterName is required when uploader parameter tracking is enabled.",
        )
  }

  private fun validateDeleteUploader(
      imageEntity: ImageEntity,
      uploaderId: String?,
  ) {
    if (!properties.uploaderParameterEnabled) {
      return
    }
    val parameterName = resolveUploaderParameterName()
    val requestUploaderId =
        uploaderId?.takeIf { it.isNotBlank() }
            ?: throw HttpStatusException(
                status = 400,
                message = "$parameterName is required when uploader parameter tracking is enabled.",
            )
    if (imageEntity.uploaderId != requestUploaderId) {
      log.warn(
          "Delete rejected due to uploader mismatch: imageId={}, requestUploaderId={}, storedUploaderId={}",
          imageEntity.id,
          requestUploaderId,
          imageEntity.uploaderId,
      )
      throw HttpStatusException(
          status = 403,
          message = "uploader parameter does not match uploaded image owner.",
      )
    }
  }

  private fun resolveUploaderParameterName(): String {
    val parameterName = properties.uploaderParameterName.trim()
    if (parameterName.isBlank()) {
      throw IllegalStateException(
          "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
      )
    }
    return parameterName
  }

  private fun resolveStoredStorageTypeForDelete(
      imageId: String,
      imageEntity: ImageEntity,
      serviceName: String,
      storageService: String,
  ): String {
    val storedStorageType = imageEntity.storageType.trim()
    if (storedStorageType.isBlank() || !storageClients.containsKey(storedStorageType)) {
      log.warn(
          "Delete rejected because stored storageType is unavailable: imageId={}, serviceName={}, storageService={}, storedStorageType={}, availableStorageTypes={}",
          imageId,
          serviceName,
          storageService,
          imageEntity.storageType,
          storageClients.keys.sorted(),
      )
      throw HttpStatusException(
          status = 400,
          message = "stored storageType is unavailable for image delete: ${imageEntity.storageType}",
      )
    }
    log.debug(
        "Resolved stored storageType for delete: imageId={}, serviceName={}, storageService={}, storedStorageType={}",
        imageId,
        serviceName,
        storageService,
        storedStorageType,
    )
    return storedStorageType
  }

  private fun resolveStorageType(
      serviceName: String,
      storageService: String,
  ): String {
    val serviceToken = serviceName.trim()
    val storageToken = storageService.trim()

    val baseCandidates =
        linkedSetOf(
            "$serviceToken:$storageToken",
            "$serviceToken::$storageToken",
            storageToken,
        )
    val candidates =
        baseCandidates
            .flatMap { listOf(it, it.uppercase(), it.lowercase()) }
            .filter { it.isNotBlank() }
            .distinct()

    return candidates.firstOrNull { storageClients.containsKey(it) }
        ?: throw HttpStatusException(
            status = 400,
            message =
                "Unknown storageType for service=$serviceName, storageService=$storageService. " +
                    "Tried candidates=$candidates",
        )
  }
}
