package com.infosung.atomic.app.storage.adapter.out.storage

import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.model.StoredImageObject
import com.infosung.atomic.app.storage.application.model.UploadImageSource
import com.infosung.atomic.app.storage.application.port.out.ImageObjectStoragePort
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.contract.log.LogStringPreview
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.image.ImageService
import kotlin.io.path.createTempFile
import org.slf4j.LoggerFactory

class ImageServiceStoragePortAdapter(
    private val imageService: ImageService,
    private val storageClients: Map<String, StorageClient>,
) : ImageObjectStoragePort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun uploadImage(
      serviceName: String,
      storageService: String,
      uploadSource: UploadImageSource,
      quality: Double,
      thumbnailEnabled: Boolean,
  ): StoredImageObject {
    val originalFilename =
        uploadSource.originalFilename?.takeIf { it.isNotBlank() }
            ?: throw InvalidImageRequestException("file original filename is required.")
    val resolvedStorageType =
        resolveUploadStorageType(serviceName = serviceName, storageService = storageService)
    val extension = originalFilename.substringAfterLast('.', "tmp")
    val tempFile = createTempFile("atomic-app-image-", ".$extension").toFile()
    try {
      uploadSource.transferTo(tempFile)
      val uploaded =
          imageService.uploadImage(
              file = tempFile,
              originFilename = originalFilename,
              storageType = resolvedStorageType,
              quality = quality,
              generateThumbnail = thumbnailEnabled,
          )
      log.debug(
          "Storage upload completed: serviceName={}, storageService={}, resolvedStorageType={}, objectKeyPreview={}, objectKeyLength={}, thumbnailKeyPreview={}, thumbnailKeyLength={}, urlLength={}, thumbnailUrlLength={}, thumbnailEnabled={}",
          serviceName,
          storageService,
          resolvedStorageType,
          LogStringPreview.summarize(uploaded.fileName),
          uploaded.fileName.length,
          LogStringPreview.summarize(uploaded.thumbnailFileName),
          uploaded.thumbnailFileName?.length ?: 0,
          uploaded.url.length,
          uploaded.thumbnailUrl?.length ?: 0,
          thumbnailEnabled,
      )
      return StoredImageObject(
          bucket = uploaded.bucket,
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
    } finally {
      tempFile.delete()
    }
  }

  override fun deleteImage(
      imageId: String,
      serviceName: String,
      storageService: String,
      storedImage: StoredImage,
  ) {
    val resolvedStorageType =
        resolveStoredStorageTypeForDelete(
            imageId = imageId,
            imageEntity = storedImage,
            serviceName = serviceName,
            storageService = storageService,
        )
    imageService.deleteImage(
        storageType = resolvedStorageType,
        fileName = storedImage.fileName,
        thumbnailFileName = storedImage.thumbnailFileName,
    )
    log.info(
        "Storage objects deleted for image delete workflow: imageId={}, storageType={}, fileNamePreview={}, thumbnailFileNamePreview={}",
        imageId,
        resolvedStorageType,
        LogStringPreview.summarize(storedImage.fileName),
        LogStringPreview.summarize(storedImage.thumbnailFileName),
    )
  }

  override fun deleteUploadedImageObject(
      storageType: String,
      fileName: String?,
      thumbnailFileName: String?,
  ) {
    imageService.deleteImage(
        storageType = storageType,
        fileName = fileName,
        thumbnailFileName = thumbnailFileName,
    )
  }

  private fun resolveUploadStorageType(
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
        ?: throw InvalidImageRequestException(
            "Unknown storageType for service=$serviceName, storageService=$storageService. Tried candidates=$candidates",
        )
  }

  private fun resolveStoredStorageTypeForDelete(
      imageId: String,
      imageEntity: StoredImage,
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
      throw InvalidImageRequestException(
          "stored storageType is unavailable for image delete: ${imageEntity.storageType}",
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
}
