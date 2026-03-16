package com.infosung.atomic.storage.image

import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ImageServiceUsageContractTest {
  @Test
  fun `uploadImage should return documented public result fields`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "R2")
    val sourceFile = tempImageFile()

    try {
      val result =
          service.uploadImage(
              file = sourceFile,
              originFilename = "hello.png",
              storageType = "R2",
          )

      assertEquals("R2", result.storageType)
      assertEquals("bucket", result.bucket)
      assertEquals("images/test/original.png", result.storageObjectKey)
      assertEquals("images/test/original.png_thumb.webp", result.storageThumbnailObjectKey)
      assertEquals("bucket/images/test/original.png", result.fileName)
      assertEquals("bucket/images/test/original.png_thumb.webp", result.thumbnailFileName)
      assertEquals("https://cdn.example.com/bucket/images/test/original.png", result.url)
      assertEquals(
          "https://cdn.example.com/bucket/images/test/original.png_thumb.webp",
          result.thumbnailUrl,
      )
      assertEquals(320, result.width)
      assertEquals(180, result.height)
      assertEquals(1024, result.fileSize)
      assertEquals(160, result.thumbnailWidth)
      assertEquals(90, result.thumbnailHeight)
      assertEquals(256, result.thumbnailFileSize)
      assertEquals(false, result.thumbnailUploadFailed)
      assertEquals(
          listOf("images/test/original.png", "images/test/original.png_thumb.webp"),
          storageClient.putRequests.map { it.objectKey },
      )
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `deleteImage should accept display keys returned from prefixed storage`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "R2")

    service.deleteImage(
        storageType = "R2",
        fileName = "bucket/images/test/original.png",
        thumbnailFileName = "bucket/images/test/original.png_thumb.webp",
    )

    assertEquals(
        listOf("images/test/original.png", "images/test/original.png_thumb.webp"),
        storageClient.deletedKeys,
    )
  }

  @Test
  fun `uploadImage should preserve public contract for input stream uploads and not close caller stream`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "S3")
    val inputStream = NonClosingInputStream(byteArrayOf(1, 2, 3, 4))

    val result =
        service.uploadImage(
            inputStream = inputStream,
            originFilename = "hello.png",
            storageType = "S3",
            quality = 1.0,
        )

    assertEquals("S3", result.storageType)
    assertEquals("images/test/original.png", result.storageObjectKey)
    assertEquals("images/test/original.png_thumb.webp", result.storageThumbnailObjectKey)
    assertEquals("images/test/original.png", result.fileName)
    assertEquals("https://cdn.example.com/images/test/original.png", result.url)
    assertEquals(
        "https://cdn.example.com/images/test/original.png_thumb.webp",
        result.thumbnailUrl,
    )
    assertFalse(inputStream.closed)
  }

  @Test
  fun `uploadImage should support disabling thumbnail generation`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "S3")
    val sourceFile = tempImageFile()

    try {
      val result =
          service.uploadImage(
              file = sourceFile,
              originFilename = "hello.png",
              storageType = "S3",
              quality = 1.0,
              generateThumbnail = false,
          )

      assertEquals("S3", result.storageType)
      assertEquals("images/test/original.png", result.storageObjectKey)
      assertEquals(null, result.storageThumbnailObjectKey)
      assertEquals("images/test/original.png", result.fileName)
      assertEquals(null, result.thumbnailFileName)
      assertEquals("https://cdn.example.com/images/test/original.png", result.url)
      assertEquals(null, result.thumbnailUrl)
      assertEquals(null, result.thumbnailWidth)
      assertEquals(null, result.thumbnailHeight)
      assertEquals(null, result.thumbnailFileSize)
      assertEquals(false, result.thumbnailUploadFailed)
      assertEquals(
          listOf("images/test/original.png"),
          storageClient.putRequests.map { it.objectKey },
      )
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should reject qualities outside documented range`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "S3")
    val sourceFile = tempImageFile()

    try {
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(
            file = sourceFile,
            originFilename = "hello.png",
            storageType = "S3",
            quality = 0.09,
        )
      }
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(
            file = sourceFile,
            originFilename = "hello.png",
            storageType = "S3",
            quality = 1.01,
        )
      }
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should accept documented quality boundaries`() {
    val storageClient = RecordingStorageClient()
    val service = createService(storageClient = storageClient, storageType = "S3")
    val sourceFile = tempImageFile()

    try {
      val low =
          service.uploadImage(
              file = sourceFile,
              originFilename = "hello.png",
              storageType = "S3",
              quality = 0.1,
          )
      val high =
          service.uploadImage(
              file = sourceFile,
              originFilename = "hello.png",
              storageType = "S3",
              quality = 1.0,
          )

      assertEquals("images/test/original.png", low.storageObjectKey)
      assertEquals("images/test/original.png", high.storageObjectKey)
    } finally {
      sourceFile.delete()
    }
  }

  private fun createService(
      storageClient: RecordingStorageClient,
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
                        prependBucketOnObjectKey = storageType == "R2",
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
        metadataReader =
            ImageMetadataReader { file ->
              if (file.extension == "webp") {
                ImageMetadata(width = 160, height = 90, size = 256)
              } else {
                ImageMetadata(width = 320, height = 180, size = 1024)
              }
            },
        thumbnailGenerator =
            ImageThumbnailGenerator { _, _, sourceObjectKey, _ ->
              val thumbnailFile = File.createTempFile("atomic-storage-contract-thumb-", ".webp")
              thumbnailFile.writeBytes(byteArrayOf(1, 2, 3))
              GeneratedThumbnail(
                  objectKey = "${sourceObjectKey}_thumb.webp",
                  file = thumbnailFile,
                  metadata = ImageMetadata(width = 160, height = 90, size = 256),
              )
            },
    )
  }

  private fun tempImageFile(): File {
    return File.createTempFile("atomic-storage-contract-source-", ".png").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
  }

  private class NonClosingInputStream(
      bytes: ByteArray,
  ) : ByteArrayInputStream(bytes) {
    var closed: Boolean = false

    override fun close() {
      closed = true
    }
  }

  private class RecordingStorageClient : StorageClient {
    val putRequests: MutableList<PutObjectRequest> = mutableListOf()
    val deletedKeys: MutableList<String> = mutableListOf()

    override fun putObject(request: PutObjectRequest) {
      putRequests += request
    }

    override fun deleteObject(objectKey: String) {
      deletedKeys += objectKey
    }
  }
}
