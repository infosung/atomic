package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
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
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )
    `when`(imageRepository.save(any(ImageEntity::class.java)))
        .thenThrow(IllegalStateException("db save failed"))

    assertFailsWith<IllegalStateException> {
      apiService.uploadImage(
          serviceName = "svc",
          storageService = "S3",
          multipartFile = multipartFile,
          quality = 1.0,
      )
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
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    val saved =
        apiService.uploadImage(
            serviceName = "svc",
            storageService = "S3",
            multipartFile = multipartFile,
            quality = 1.0,
            uploaderId = "member-100",
        )

    assertEquals("member-100", saved.uploaderId)
    verify(imageRepository).save(any(ImageEntity::class.java))
  }

  @Test
  fun `uploadImage should leave thumbnail fields empty when thumbnail generation is disabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          thumbnailEnabled = false
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )
    `when`(imageRepository.save(any(ImageEntity::class.java))).thenAnswer { it.arguments[0] }

    val saved =
        apiService.uploadImage(
            serviceName = "svc",
            storageService = "S3",
            multipartFile = multipartFile,
            quality = 1.0,
        )

    assertEquals(listOf("images/test/original.png"), storageClient.putObjectKeys)
    assertEquals(null, saved.thumbnailFileName)
    assertEquals(null, saved.thumbnailUrl)
    assertEquals(null, saved.thumbnailWidth)
    assertEquals(null, saved.thumbnailHeight)
    assertEquals(null, saved.thumbnailFileSize)
  }

  @Test
  fun `uploadImage should return 400 when uploader parameter is missing and tracking is enabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )

    val exception =
        assertFailsWith<HttpStatusException> {
          apiService.uploadImage(
              serviceName = "svc",
              storageService = "S3",
              multipartFile = multipartFile,
              quality = 1.0,
              uploaderId = null,
          )
        }

    assertEquals(400, exception.status)
  }

  @Test
  fun `uploadImage should return 400 when quality is outside configured range`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          minQuality = 0.4
          maxQuality = 0.8
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )

    val exception =
        assertFailsWith<HttpStatusException> {
          apiService.uploadImage(
              serviceName = "svc",
              storageService = "S3",
              multipartFile = multipartFile,
              quality = 0.2,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("quality must be in range 0.4..0.8", exception.message)
    assertTrue(storageClient.putObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should delete storage objects then metadata`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )
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

    apiService.deleteImage(
        serviceName = "svc",
        storageService = "s3",
        imageId = imageId.toString(),
    )

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
    val imageEntity = newImageEntity(imageId = imageId, status = "ACTIVE")
    val imageEntityTxService = FakeImageEntityTxService(imageEntity)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )

    apiService.deleteImage(
        serviceName = "svc",
        storageService = "S3",
        imageId = imageId.toString(),
    )

    assertEquals(listOf("DELETE_PENDING"), imageEntityTxService.savedStatuses)
    assertEquals(listOf(imageId), imageEntityTxService.deletedPendingIds)
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
    val imageEntity = newImageEntity(imageId = imageId, status = "ACTIVE")
    val imageEntityTxService = FakeImageEntityTxService(imageEntity)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )

    assertFailsWith<IllegalStateException> {
      apiService.deleteImage(
          serviceName = "svc",
          storageService = "S3",
          imageId = imageId.toString(),
      )
    }

    assertEquals(listOf("DELETE_PENDING"), imageEntityTxService.savedStatuses)
    assertTrue(imageEntityTxService.deletedPendingIds.isEmpty())
    assertEquals("DELETE_PENDING", imageEntityTxService.currentEntity.status)
  }

  @Test
  fun `deleteImage should retry storage cleanup when metadata is already delete pending`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageEntity = newImageEntity(imageId = imageId, status = "DELETE_PENDING")
    val imageEntityTxService = FakeImageEntityTxService(imageEntity)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )

    apiService.deleteImage(
        serviceName = "svc",
        storageService = "S3",
        imageId = imageId.toString(),
    )

    assertTrue(imageEntityTxService.savedStatuses.isEmpty())
    assertEquals(listOf(imageId), imageEntityTxService.deletedPendingIds)
    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
  }

  @Test
  fun `deleteImage should return 400 when persisted storage type is unavailable`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "svc:S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("svc:S3" to storageClient),
            properties = AtomicAppImageProperties(),
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

    val exception =
        assertFailsWith<HttpStatusException> {
          apiService.deleteImage(
              serviceName = "svc",
              storageService = "S3",
              imageId = imageId.toString(),
          )
        }

    assertEquals(400, exception.status)
    assertEquals(
        "stored storageType is unavailable for image delete: UNKNOWN",
        exception.message,
    )
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
    verify(imageRepository, never()).delete(any(ImageEntity::class.java))
  }

  @Test
  fun `deleteImage should return 400 when imageId is invalid uuid`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = AtomicAppImageProperties(),
        )

    val exception =
        assertFailsWith<HttpStatusException> {
          apiService.deleteImage(
              serviceName = "svc",
              storageService = "S3",
              imageId = "not-a-uuid",
          )
        }

    assertEquals(400, exception.status)
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should return 403 when uploader does not match and tracking is enabled`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
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
        assertFailsWith<HttpStatusException> {
          apiService.deleteImage(
              serviceName = "svc",
              storageService = "S3",
              imageId = imageId.toString(),
              uploaderId = "member-200",
          )
        }

    assertEquals(403, exception.status)
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should return 400 when uploader is missing and tracking is enabled`() {
    val imageId = UUID.randomUUID()
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
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
        assertFailsWith<HttpStatusException> {
          apiService.deleteImage(
              serviceName = "svc",
              storageService = "S3",
              imageId = imageId.toString(),
              uploaderId = null,
          )
        }

    assertEquals(400, exception.status)
    assertEquals("memberId is required when uploader parameter tracking is enabled.", exception.message)
    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `uploadImage should reject blank uploader parameter name when tracking is enabled`() {
    val storageClient = CapturingStorageClient()
    val imageRepository = mock(ImageRepository::class.java)
    val imageEntityTxService = AppImageEntityTxService(imageRepository)
    val imageService = createImageService(storageClient = storageClient, storageType = "S3")
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = " "
        }
    val apiService =
        AppImageApiService(
            imageEntityTxService = imageEntityTxService,
            imageService = imageService,
            storageClients = mapOf("S3" to storageClient),
            properties = properties,
        )
    val multipartFile =
        MockMultipartFile(
            "file",
            "profile.png",
            "image/png",
            "dummy-image".toByteArray(),
        )

    val exception =
        assertFailsWith<IllegalStateException> {
          apiService.uploadImage(
              serviceName = "svc",
              storageService = "S3",
              multipartFile = multipartFile,
              quality = 1.0,
              uploaderId = "member-100",
          )
        }

    assertEquals(
        "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
        exception.message,
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

  private class CapturingStorageClient : StorageClient {
    constructor() : this(failDeleteObject = { false })

    constructor(failDeleteObject: (String) -> Boolean) {
      this.failDeleteObject = failDeleteObject
    }

    val putObjectKeys: MutableList<String> = mutableListOf()
    val deletedObjectKeys: MutableList<String> = mutableListOf()
    private var failDeleteObject: (String) -> Boolean = { false }

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

  private fun newImageEntity(
      imageId: UUID,
      status: String,
  ): ImageEntity {
    return ImageEntity(
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

  private class FakeImageEntityTxService(
      initialEntity: ImageEntity,
  ) : AppImageEntityTxService(mock(ImageRepository::class.java)) {
    var currentEntity: ImageEntity = initialEntity
    val savedStatuses: MutableList<String> = mutableListOf()
    val deletedPendingIds: MutableList<UUID> = mutableListOf()

    override fun findByIdOrThrow(
        imageId: UUID,
        rawImageId: String,
    ): ImageEntity {
      return currentEntity
    }

    override fun save(imageEntity: ImageEntity): ImageEntity {
      currentEntity = imageEntity
      savedStatuses += imageEntity.status
      return imageEntity
    }

    override fun delete(imageEntity: ImageEntity) {
      deletedPendingIds += requireNotNull(imageEntity.id)
    }
  }
}
