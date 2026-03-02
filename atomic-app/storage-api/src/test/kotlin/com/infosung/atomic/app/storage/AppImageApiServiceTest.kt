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

    apiService.deleteImage(
        serviceName = "svc",
        storageService = "s3",
        imageId = imageId.toString(),
    )

    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
    verify(imageRepository).delete(savedEntity)
  }

  @Test
  fun `deleteImage should use fallback resolved storage type when entity storage type is missing`() {
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

    apiService.deleteImage(
        serviceName = "svc",
        storageService = "S3",
        imageId = imageId.toString(),
    )

    assertEquals(
        listOf("images/test/original.png", "images/test/original_thumb.webp"),
        storageClient.deletedObjectKeys,
    )
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
    val putObjectKeys: MutableList<String> = mutableListOf()
    val deletedObjectKeys: MutableList<String> = mutableListOf()

    override fun putObject(request: PutObjectRequest) {
      putObjectKeys += request.objectKey
    }

    override fun deleteObject(objectKey: String) {
      deletedObjectKeys += objectKey
    }
  }
}
