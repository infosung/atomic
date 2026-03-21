package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository
import com.infosung.atomic.app.storage.adapter.out.storage.ImageServiceStoragePortAdapter
import com.infosung.atomic.app.storage.application.exception.ImageOwnershipMismatchException
import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.model.AppImageRequestPolicy
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.service.AppImageApiService
import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.GeneratedThumbnail
import com.infosung.atomic.storage.image.ImageInputValidator
import com.infosung.atomic.storage.image.ImageMetadata
import com.infosung.atomic.storage.image.ImageMetadataReader
import com.infosung.atomic.storage.image.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.image.ImageThumbnailGenerator
import com.infosung.atomic.storage.image.ValidatedImageInput
import java.io.File
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile

class AppImageApiServiceTest {
  @Test
  fun `uploadImage should compensate storage objects when metadata save fails`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val apiService = newApiService(storageClient, imageMetadataPort)
    val multipartFile = sampleMultipartFile()
    `when`(imageRepository.save(any(ImageEntity::class.java)))
        .thenThrow(IllegalStateException("db save failed"))

    assertFailsWith<IllegalStateException> {
      apiService.uploadImage(uploadCommand(multipartFile = multipartFile))
    }

    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.putObjectKeys,
    )
    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
  }

  @Test
  fun `uploadImage should store uploaderId when uploader tracking is enabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    val saved =
        apiService.uploadImage(
            uploadCommand(
                multipartFile = sampleMultipartFile(),
                uploaderId = "member-100",
            ),
        )

    assertEquals("member-100", saved.uploaderId)
    verify(imageRepository).save(any(ImageEntity::class.java))
  }

  @Test
  fun `uploadImage should leave thumbnail fields empty when thumbnail generation is disabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties = AtomicAppImageProperties().apply { thumbnailEnabled = false }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    val saved =
        apiService.uploadImage(
            uploadCommand(
                multipartFile = sampleMultipartFile(),
                thumbnailEnabled = false,
            ),
        )

    assertEquals(listOf("images/test/original.png"), storageClient.putObjectKeys)
    assertEquals(null, saved.thumbnailFileName)
    assertEquals(null, saved.thumbnailUrl)
    assertEquals(null, saved.thumbnailWidth)
    assertEquals(null, saved.thumbnailHeight)
    assertEquals(null, saved.thumbnailFileSize)
  }

  @Test
  fun `uploadImage should return invalid request when uploader parameter is missing and tracking is enabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          apiService.uploadImage(
              uploadCommand(
                  multipartFile = sampleMultipartFile(),
                  uploaderId = null,
              ),
          )
        }

    assertEquals(
        "memberId is required when uploader parameter tracking is enabled.",
        exception.message,
    )
  }

  @Test
  fun `uploadImage should return invalid request when quality is outside configured range`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          minQuality = 0.4
          maxQuality = 0.8
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          apiService.uploadImage(
              uploadCommand(
                  multipartFile = sampleMultipartFile(),
                  quality = 0.2,
              ),
          )
        }

    assertEquals("quality must be in range 0.4..0.8", exception.message)
    assertTrue(storageClient.putObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should delete storage objects then metadata`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val apiService = newApiService(storageClient, imageMetadataPort)
    val savedEntity =
        ImageEntity(
            id = imageId,
            bucket = "bucket",
            serviceName = "svc",
            storageService = "S3",
            storageType = "S3",
            fileName = "images/test/original.png",
            thumbnailFileName = "images/test/original_thumb.webp",
            url = "https://cdn/images/test/original.png",
            thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
            fileSize = 123,
        )
    `when`(imageRepository.findById(imageId)).thenReturn(Optional.of(savedEntity))
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    apiService.deleteImage(deleteCommand(imageId = imageId.toString(), storageService = "s3"))

    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
    verify(imageRepository).save(any(ImageEntity::class.java))
    verify(imageRepository).delete(any(ImageEntity::class.java))
  }

  @Test
  fun `deleteImage should reserve delete pending before deleting storage and purge metadata after success`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageMetadataPort =
        FakeImageMetadataPort(newStoredImage(imageId = imageId, status = "ACTIVE"))
    val apiService = newApiService(storageClient, imageMetadataPort)

    apiService.deleteImage(deleteCommand(imageId = imageId.toString()))

    assertEquals(listOf("DELETE_PENDING"), imageMetadataPort.savedStatuses)
    assertEquals(listOf(imageId), imageMetadataPort.purgedIds)
    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
  }

  @Test
  fun `deleteImage should keep metadata in delete pending when storage delete fails`() {
    val imageId = UUID.randomUUID()
    val storageClient =
        CapturingStorageClient(
            failDeleteObject = { key -> key == "images/test/original.png" },
        )
    val imageMetadataPort =
        FakeImageMetadataPort(newStoredImage(imageId = imageId, status = "ACTIVE"))
    val apiService = newApiService(storageClient, imageMetadataPort)

    assertFailsWith<IllegalStateException> {
      apiService.deleteImage(deleteCommand(imageId = imageId.toString()))
    }

    assertEquals(listOf("DELETE_PENDING"), imageMetadataPort.savedStatuses)
    assertTrue(imageMetadataPort.purgedIds.isEmpty())
    assertEquals("DELETE_PENDING", imageMetadataPort.currentImage.status)
  }

  @Test
  fun `deleteImage should retry storage cleanup when metadata is already delete pending`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageMetadataPort =
        FakeImageMetadataPort(newStoredImage(imageId = imageId, status = "DELETE_PENDING"))
    val apiService = newApiService(storageClient, imageMetadataPort)

    apiService.deleteImage(deleteCommand(imageId = imageId.toString()))

    assertTrue(imageMetadataPort.savedStatuses.isEmpty())
    assertEquals(listOf(imageId), imageMetadataPort.purgedIds)
    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
  }

  @Test
  fun `deleteImage should return invalid request when persisted storage type is unavailable`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val apiService =
        newApiService(
            storageClient = storageClient,
            imageMetadataPort = imageMetadataPort,
            configuredStorageType = "svc:S3",
        )
    val savedEntity =
        ImageEntity(
            id = imageId,
            bucket = "bucket",
            serviceName = "svc",
            storageService = "S3",
            storageType = "UNKNOWN",
            fileName = "images/test/original.png",
            thumbnailFileName = "images/test/original_thumb.webp",
            url = "https://cdn/images/test/original.png",
            thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
            fileSize = 123,
        )
    `when`(imageRepository.findById(imageId)).thenReturn(Optional.of(savedEntity))
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          apiService.deleteImage(deleteCommand(imageId = imageId.toString()))
        }

    assertEquals(
        "stored storageType is unavailable for image delete: UNKNOWN",
        exception.message,
    )
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
    verify(imageRepository, never()).delete(savedEntity)
  }

  @Test
  fun `deleteImage should return invalid request when imageId is invalid uuid`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val apiService = newApiService(storageClient, imageMetadataPort)

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          apiService.deleteImage(deleteCommand(imageId = "not-a-uuid"))
        }

    assertEquals("imageId must be a valid UUID.", exception.message)
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should return ownership mismatch when uploader does not match and tracking is enabled`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)
    val savedEntity =
        ImageEntity(
            id = imageId,
            bucket = "bucket",
            serviceName = "svc",
            storageService = "S3",
            uploaderId = "member-100",
            storageType = "S3",
            fileName = "images/test/original.png",
            thumbnailFileName = "images/test/original_thumb.webp",
            url = "https://cdn/images/test/original.png",
            thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
            fileSize = 123,
        )
    `when`(imageRepository.findById(imageId)).thenReturn(Optional.of(savedEntity))

    val exception =
        assertFailsWith<ImageOwnershipMismatchException> {
          apiService.deleteImage(
              deleteCommand(imageId = imageId.toString(), uploaderId = "member-200"),
          )
        }

    assertEquals("uploader parameter does not match uploaded image owner.", exception.message)
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should return invalid request when uploader is missing and tracking is enabled`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)
    val savedEntity =
        ImageEntity(
            id = imageId,
            bucket = "bucket",
            serviceName = "svc",
            storageService = "S3",
            uploaderId = "member-100",
            storageType = "S3",
            fileName = "images/test/original.png",
            thumbnailFileName = "images/test/original_thumb.webp",
            url = "https://cdn/images/test/original.png",
            thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
            fileSize = 123,
        )
    `when`(imageRepository.findById(imageId)).thenReturn(Optional.of(savedEntity))

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          apiService.deleteImage(deleteCommand(imageId = imageId.toString(), uploaderId = null))
        }

    assertEquals(
        "memberId is required when uploader parameter tracking is enabled.",
        exception.message,
    )
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `uploadImage should reject blank uploader parameter name when tracking is enabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageMetadataPort = AppImageEntityTxService(imageRepository)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = " "
        }
    val apiService = newApiService(storageClient, imageMetadataPort, properties)

    val exception =
        assertFailsWith<IllegalStateException> {
          apiService.uploadImage(
              uploadCommand(
                  multipartFile = sampleMultipartFile(),
                  uploaderId = "member-100",
              ),
          )
        }

    assertEquals(
        "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
        exception.message,
    )
  }

  private fun newApiService(
      storageClient: StorageClient,
      imageMetadataPort: com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort,
      properties: AtomicAppImageProperties = AtomicAppImageProperties(),
      configuredStorageType: String = "S3",
  ): AppImageApiService {
    val imageService =
        createImageService(storageClient = storageClient, storageType = configuredStorageType)
    val imageObjectStoragePort =
        ImageServiceStoragePortAdapter(
            imageService = imageService,
            storageClients = mapOf(configuredStorageType to storageClient),
        )
    return AppImageApiService(
        imageMetadataPort = imageMetadataPort,
        imageObjectStoragePort = imageObjectStoragePort,
        requestPolicy = requestPolicy(properties),
    )
  }

  private fun requestPolicy(properties: AtomicAppImageProperties): AppImageRequestPolicy {
    return AppImageRequestPolicy(
        minQuality = properties.minQuality,
        maxQuality = properties.maxQuality,
        uploaderParameterEnabled = properties.uploaderParameterEnabled,
        uploaderParameterName = properties.uploaderParameterName,
    )
  }

  private fun sampleMultipartFile(): MockMultipartFile {
    return MockMultipartFile(
        "file",
        "profile.png",
        "image/png",
        "dummy-image".toByteArray(),
    )
  }

  private fun uploadCommand(
      multipartFile: MockMultipartFile,
      quality: Double = 1.0,
      uploaderId: String? = null,
      thumbnailEnabled: Boolean = true,
      serviceName: String = "svc",
      storageService: String = "S3",
  ): UploadAppImageCommand {
    return UploadAppImageCommand(
        serviceName = serviceName,
        storageService = storageService,
        quality = quality,
        uploaderId = uploaderId,
        thumbnailEnabled = thumbnailEnabled,
        uploadSource =
            object : com.infosung.atomic.app.storage.application.model.UploadImageSource {
              override val originalFilename: String?
                get() = multipartFile.originalFilename

              override fun transferTo(destinationFile: File) {
                multipartFile.transferTo(destinationFile)
              }
            },
    )
  }

  private fun deleteCommand(
      imageId: String,
      serviceName: String = "svc",
      storageService: String = "S3",
      uploaderId: String? = null,
  ): DeleteAppImageCommand {
    return DeleteAppImageCommand(
        serviceName = serviceName,
        storageService = storageService,
        imageId = imageId,
        uploaderId = uploaderId,
    )
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
              GeneratedThumbnail(
                  objectKey = "images/test/original_thumb.webp",
                  file = thumbnailFile,
                  metadata = ImageMetadata(width = 50, height = 40, size = thumbnailFile.length()),
              )
            },
    )
  }

  private class CapturingStorageClient(
      private val failDeleteObject: (String) -> Boolean = { false },
  ) : StorageClient {
    val putObjectKeys: MutableList<String> = mutableListOf()
    val deletedObjectKeys: MutableList<String> = mutableListOf()

    override fun putObject(request: PutObjectRequest) {
      putObjectKeys += request.objectKey
    }

    override fun deleteObject(objectKey: String) {
      if (failDeleteObject(objectKey)) {
        throw IllegalStateException("delete failed for $objectKey")
      }
      deletedObjectKeys += objectKey
    }
  }

  private fun newStoredImage(
      imageId: UUID,
      status: String,
  ): StoredImage {
    return StoredImage(
        id = imageId,
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = status,
        storageType = "S3",
        fileName = "images/test/original.png",
        thumbnailFileName = "images/test/original_thumb.webp",
        url = "https://cdn/images/test/original.png",
        thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
        fileSize = 123,
    )
  }

  private class FakeImageMetadataPort(
      initialImage: StoredImage,
  ) : com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort {
    var currentImage: StoredImage = initialImage
    val savedStatuses: MutableList<String> = mutableListOf()
    val purgedIds: MutableList<UUID> = mutableListOf()

    override fun findByIdOrThrow(
        imageId: UUID,
        rawImageId: String,
    ): StoredImage = currentImage

    override fun save(image: StoredImage): StoredImage {
      currentImage = image
      savedStatuses += image.status
      return image
    }

    override fun markDeletePending(image: StoredImage): StoredImage {
      if (image.status == StoredImage.STATUS_DELETE_PENDING) {
        currentImage = image
        return image
      }
      val deletePending = image.toDeletePending()
      currentImage = deletePending
      savedStatuses += deletePending.status
      return deletePending
    }

    override fun purgeDeletePending(image: StoredImage) {
      purgedIds += requireNotNull(image.id)
    }

    override fun inspectDeletePendingImages() =
        throw UnsupportedOperationException("not needed in this test")

    override fun claimDeletePending(
        limit: Int,
        claimToken: String,
        claimedAt: java.time.LocalDateTime
    ) = throw UnsupportedOperationException("not needed in this test")

    override fun releaseDeleteRecoveryClaim(imageId: UUID, claimToken: String) =
        throw UnsupportedOperationException("not needed in this test")
  }
}
