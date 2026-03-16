package com.infosung.atomic.app.storage

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.ImageInputValidator
import com.infosung.atomic.storage.image.ImageMetadata
import com.infosung.atomic.storage.image.ImageMetadataReader
import com.infosung.atomic.storage.image.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.image.ImageThumbnailGenerator
import com.infosung.atomic.storage.image.ValidatedImageInput
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock

class AppImageDeleteRecoveryServiceTest {
  @Test
  fun `recoverDeletePendingImages should purge recovered rows up to limit`() {
    val image1 = newDeletePendingImageEntity()
    val image2 = newDeletePendingImageEntity()
    val image3 = newDeletePendingImageEntity()
    val storageClient = CapturingStorageClient()
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val txService = FakeRecoveryImageEntityTxService(listOf(image1, image2, image3))
    val recoveryService = AppImageDeleteRecoveryService(txService, imageService)

    val result = recoveryService.recoverDeletePendingImages(limit = 2)

    assertEquals(2, result.scannedCount)
    assertEquals(2, result.recoveredCount)
    assertEquals(0, result.failedCount)
    assertEquals(listOf(requireNotNull(image1.id), requireNotNull(image2.id)), txService.purgedIds)
    assertEquals(listOf(requireNotNull(image3.id)), txService.remainingIds())
  }

  @Test
  fun `recoverDeletePendingImages should continue after one item fails`() {
    val image1 = newDeletePendingImageEntity(fileName = "images/test/success-1.png")
    val image2 = newDeletePendingImageEntity(fileName = "images/test/fail.png")
    val image3 = newDeletePendingImageEntity(fileName = "images/test/success-2.png")
    val storageClient =
        CapturingStorageClient(
            failDeleteObject = { key -> key == "images/test/fail.png" },
        )
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val txService = FakeRecoveryImageEntityTxService(listOf(image1, image2, image3))
    val recoveryService = AppImageDeleteRecoveryService(txService, imageService)

    val result = recoveryService.recoverDeletePendingImages(limit = 10)

    assertEquals(3, result.scannedCount)
    assertEquals(2, result.recoveredCount)
    assertEquals(1, result.failedCount)
    assertEquals(
        listOf(requireNotNull(image1.id), requireNotNull(image3.id)),
        txService.purgedIds,
    )
    assertEquals(listOf(requireNotNull(image2.id)), txService.remainingIds())
  }

  @Test
  fun `recoverDeletePendingImages should reject non positive limit`() {
    val imageService =
        createImageService(storageClient = CapturingStorageClient(), storageType = "S3")
    val txService = FakeRecoveryImageEntityTxService(emptyList())
    val recoveryService = AppImageDeleteRecoveryService(txService, imageService)

    val exception =
        assertFailsWith<IllegalArgumentException> {
          recoveryService.recoverDeletePendingImages(limit = 0)
        }

    assertEquals("limit must be greater than zero.", exception.message)
  }

  private fun createImageService(
      storageClient: StorageClient,
      storageType: String,
  ): ImageService {
    return ImageService(
        storageClients = mapOf(storageType to storageClient),
        storageProfiles =
            mapOf(
                storageType to
                    StorageProfile(
                        bucket = "bucket",
                        cdn = "https://cdn.example.com",
                        prependBucketOnObjectKey = false,
                    ),
            ),
        objectKeyGenerator = ImageObjectKeyGenerator { "images/test/original.png" },
        imageInputValidator =
            ImageInputValidator { _, _ ->
              ValidatedImageInput(
                  extension = "png",
                  contentType = "image/png",
                  detectedFormat = "PNG",
              )
            },
        metadataReader = ImageMetadataReader { ImageMetadata(width = 100, height = 80, size = 12) },
        thumbnailGenerator =
            ImageThumbnailGenerator { _, _, _, _ ->
              val thumbnailFile = File.createTempFile("atomic-app-storage-thumb-", ".webp")
              thumbnailFile.writeBytes(byteArrayOf(1, 2, 3))
              com.infosung.atomic.storage.image.GeneratedThumbnail(
                  objectKey = "images/test/original_thumb.webp",
                  file = thumbnailFile,
                  metadata = ImageMetadata(width = 50, height = 40, size = thumbnailFile.length()),
              )
            },
    )
  }

  private fun newDeletePendingImageEntity(
      fileName: String = "images/test/original.png",
      thumbnailFileName: String = "images/test/original_thumb.webp",
  ): ImageEntity {
    return ImageEntity(
        id = UUID.randomUUID(),
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = ImageEntity.STATUS_DELETE_PENDING,
        storageType = "S3",
        fileName = fileName,
        thumbnailFileName = thumbnailFileName,
        url = "https://cdn.example.com/$fileName",
        thumbnailUrl = "https://cdn.example.com/$thumbnailFileName",
        fileSize = 123,
    )
  }

  private class FakeRecoveryImageEntityTxService(
      initialEntities: List<ImageEntity>,
  ) : AppImageEntityTxService(mock(ImageRepository::class.java)) {
    private val pendingEntities: MutableList<ImageEntity> = initialEntities.toMutableList()
    val purgedIds: MutableList<UUID> = mutableListOf()

    override fun findDeletePending(limit: Int): List<ImageEntity> =
        pendingEntities.take(limit).toList()

    override fun purgeDeletePending(imageEntity: ImageEntity) {
      val imageId = requireNotNull(imageEntity.id)
      purgedIds += imageId
      pendingEntities.removeIf { it.id == imageId }
    }

    fun remainingIds(): List<UUID> = pendingEntities.mapNotNull { it.id }
  }

  private class CapturingStorageClient(
      private val failDeleteObject: (String) -> Boolean = { false },
  ) : StorageClient {
    val deletedObjectKeys: MutableList<String> = mutableListOf()

    override fun putObject(request: PutObjectRequest) = Unit

    override fun deleteObject(objectKey: String) {
      if (failDeleteObject(objectKey)) {
        throw IllegalStateException("delete failed for $objectKey")
      }
      deletedObjectKeys += objectKey
    }
  }
}
