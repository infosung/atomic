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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageServiceTest {
  @Test
  fun `uploadImage should succeed when thumbnail generation fails`() {
    val storageClient = CapturingStorageClient()
    val service =
        createImageService(
            storageClient = storageClient, thumbnailGenerator = failingThumbnailGenerator())
    val sourceFile = tempFile()

    try {
      val result =
          service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")

      assertEquals(
          listOf("images/test/original.png"), storageClient.putRequests.map { it.objectKey })
      assertEquals("image/png", storageClient.putRequests.single().contentType)
      assertEquals("100", storageClient.putRequests.single().metadata["width"])
      assertTrue(result.thumbnailUploadFailed)
      assertEquals(result.thumbnailFailureReason?.contains("IllegalStateException"), true)
      assertEquals(null, result.thumbnailFileName)
      assertEquals("images/test/original.png", result.storageObjectKey)
      assertEquals(null, result.storageThumbnailObjectKey)
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should succeed even when thumbnail upload fails`() {
    val storageClient =
        CapturingStorageClient(
            failPutObject = { key -> key.endsWith("_thumb.webp") },
        )
    val service =
        createImageService(
            storageClient = storageClient, thumbnailGenerator = successfulThumbnailGenerator())
    val sourceFile = tempFile()

    try {
      val result =
          service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")

      assertEquals(
          listOf("images/test/original.png", "images/test/original.png_thumb.webp"),
          storageClient.putRequests.map { it.objectKey },
      )
      assertTrue(result.thumbnailUploadFailed)
      assertTrue(result.thumbnailFailureReason?.contains("thumbnail upload failed") == true)
      assertEquals(null, result.storageThumbnailObjectKey)
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should upload thumbnail when generation succeeds`() {
    val storageClient = CapturingStorageClient()
    var generatedThumbnailFile: File? = null
    val thumbnailGenerator = ImageThumbnailGenerator { _, _, objectKey, _ ->
      val temp = File.createTempFile("atomic-storage-thumb-", ".webp")
      temp.writeBytes(byteArrayOf(9, 9, 9))
      generatedThumbnailFile = temp
      GeneratedThumbnail(
          objectKey = "${objectKey}_thumb.webp",
          file = temp,
          metadata = ImageMetadata(width = 30, height = 20, size = temp.length()),
      )
    }
    val service =
        createImageService(storageClient = storageClient, thumbnailGenerator = thumbnailGenerator)
    val sourceFile = tempFile()

    try {
      val result =
          service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")

      assertEquals(
          listOf("images/test/original.png", "images/test/original.png_thumb.webp"),
          storageClient.putRequests.map { it.objectKey },
      )
      assertFalse(result.thumbnailUploadFailed)
      assertEquals("images/test/original.png_thumb.webp", result.thumbnailFileName)
      assertEquals("images/test/original.png_thumb.webp", result.storageThumbnailObjectKey)
      assertEquals(30, result.thumbnailWidth)
      assertEquals(20, result.thumbnailHeight)
      assertNotNull(generatedThumbnailFile)
      assertFalse(generatedThumbnailFile.exists())
    } finally {
      sourceFile.delete()
      generatedThumbnailFile?.delete()
    }
  }

  @Test
  fun `uploadImage should include bucket prefix in returned names when configured`() {
    val storageClient = CapturingStorageClient()
    val service =
        createImageService(
            storageClient = storageClient,
            storageProfiles =
                mapOf(
                    "R2" to
                        StorageProfile(
                            bucket = "bucket",
                            cdn = "https://cdn",
                            prependBucketOnObjectKey = true,
                        ),
                ),
            storageClients = mapOf("R2" to storageClient),
        )
    val sourceFile = tempFile()

    try {
      val result =
          service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "R2")
      assertEquals("bucket/images/test/original.png", result.fileName)
      assertEquals("https://cdn/bucket/images/test/original.png", result.url)
      assertEquals("bucket/images/test/original.png_thumb.webp", result.thumbnailFileName)
      assertEquals(
          "https://cdn/bucket/images/test/original.png_thumb.webp",
          result.thumbnailUrl,
      )
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should avoid double slash when cdn ends with slash`() {
    val storageClient = CapturingStorageClient()
    val service =
        createImageService(
            storageClient = storageClient,
            storageProfiles = mapOf("S3" to StorageProfile(bucket = "bucket", cdn = "https://cdn/")),
        )
    val sourceFile = tempFile()

    try {
      val result =
          service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")
      assertEquals("https://cdn/images/test/original.png", result.url)
      assertEquals("https://cdn/images/test/original.png_thumb.webp", result.thumbnailUrl)
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should support input stream source`() {
    val storageClient = CapturingStorageClient()
    val service = createImageService(storageClient = storageClient)
    val inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))

    val result =
        service.uploadImage(
            inputStream = inputStream,
            originFilename = "hello.png",
            storageType = "S3",
        )

    assertEquals("images/test/original.png", result.storageObjectKey)
    assertEquals(
        listOf("images/test/original.png", "images/test/original.png_thumb.webp"),
        storageClient.putRequests.map { it.objectKey },
    )
  }

  @Test
  fun `uploadImage from input stream should not close caller owned stream`() {
    val storageClient = CapturingStorageClient()
    val service = createImageService(storageClient = storageClient)
    val inputStream = NonClosingInputStream(byteArrayOf(1, 2, 3, 4))

    service.uploadImage(
        inputStream = inputStream,
        originFilename = "hello.png",
        storageType = "S3",
    )

    assertFalse(inputStream.closed)
  }

  @Test
  fun `uploadImage should not upload original when metadata reading fails`() {
    val storageClient = CapturingStorageClient()
    val metadataReader = ImageMetadataReader { throw IllegalArgumentException("invalid image") }
    val service = createImageService(storageClient = storageClient, metadataReader = metadataReader)
    val sourceFile = tempFile()

    try {
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")
      }
      assertTrue(storageClient.putRequests.isEmpty())
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should fail when validator rejects extension mismatch`() {
    val storageClient = CapturingStorageClient()
    val imageInputValidator = ImageInputValidator { _, _ ->
      throw IllegalArgumentException("Image extension does not match detected format")
    }
    val service =
        createImageService(storageClient = storageClient, imageInputValidator = imageInputValidator)
    val sourceFile = tempFile()

    try {
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(file = sourceFile, originFilename = "hello.jpg", storageType = "S3")
      }
      assertTrue(storageClient.putRequests.isEmpty())
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should fail when storage type is unknown`() {
    val service = createImageService(storageClient = CapturingStorageClient())
    val sourceFile = tempFile()
    try {
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(
            file = sourceFile, originFilename = "hello.png", storageType = "UNKNOWN")
      }
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should fail when source file does not exist`() {
    val service = createImageService(storageClient = CapturingStorageClient())
    val sourceFile =
        File(
            File(System.getProperty("java.io.tmpdir")),
            "atomic-storage-missing-${System.nanoTime()}.tmp",
        )

    assertFailsWith<IllegalArgumentException> {
      service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")
    }
  }

  @Test
  fun `uploadImage should fail when quality is out of range`() {
    val service = createImageService(storageClient = CapturingStorageClient())
    val sourceFile = tempFile()

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
  fun `uploadImage should fail when storage profile is missing`() {
    val service =
        createImageService(
            storageClient = CapturingStorageClient(),
            storageClients = mapOf("S3" to CapturingStorageClient()),
            storageProfiles = emptyMap(),
        )
    val sourceFile = tempFile()
    try {
      assertFailsWith<IllegalArgumentException> {
        service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")
      }
    } finally {
      sourceFile.delete()
    }
  }

  @Test
  fun `uploadImage should propagate interrupted exception and keep interrupted status`() {
    val storageClient = CapturingStorageClient()
    val interruptingThumbnailGenerator = ImageThumbnailGenerator { _, _, _, _ ->
      throw InterruptedException("interrupted")
    }
    val service =
        createImageService(
            storageClient = storageClient,
            thumbnailGenerator = interruptingThumbnailGenerator,
        )
    val sourceFile = tempFile()

    Thread.interrupted()
    try {
      assertFailsWith<InterruptedException> {
        service.uploadImage(file = sourceFile, originFilename = "hello.png", storageType = "S3")
      }
      assertTrue(Thread.currentThread().isInterrupted)
    } finally {
      sourceFile.delete()
      Thread.interrupted()
    }
  }

  @Test
  fun `deleteImage should normalize prefixed key for bucket configured storage`() {
    val storageClient = CapturingStorageClient()
    val service =
        createImageService(
            storageClient = storageClient,
            storageProfiles =
                mapOf(
                    "R2" to
                        StorageProfile(
                            bucket = "bucket",
                            cdn = "https://cdn",
                            prependBucketOnObjectKey = true,
                        ),
                ),
            storageClients = mapOf("R2" to storageClient),
        )

    service.deleteImage(
        storageType = "R2",
        fileName = "bucket/images/a.png",
        thumbnailFileName = "bucket/images/a_thumb.webp",
    )

    assertEquals(listOf("images/a.png", "images/a_thumb.webp"), storageClient.deletedObjectKeys)
  }

  @Test
  fun `deleteImage should ignore blank keys`() {
    val storageClient = CapturingStorageClient()
    val service = createImageService(storageClient = storageClient)

    service.deleteImage(storageType = "S3", fileName = " ", thumbnailFileName = "")

    assertTrue(storageClient.deletedObjectKeys.isEmpty())
  }

  @Test
  fun `deleteImage should fail when storage type is unknown`() {
    val service = createImageService(storageClient = CapturingStorageClient())

    assertFailsWith<IllegalArgumentException> {
      service.deleteImage(storageType = "UNKNOWN", fileName = "images/a.png")
    }
  }

  private fun createImageService(
      storageClient: StorageClient,
      storageClients: Map<String, StorageClient> = mapOf("S3" to storageClient),
      storageProfiles: Map<String, StorageProfile> =
          mapOf("S3" to StorageProfile(bucket = "bucket", cdn = "https://cdn")),
      imageInputValidator: ImageInputValidator = defaultImageInputValidator(),
      metadataReader: ImageMetadataReader = ImageMetadataReader { ImageMetadata(100, 80, 10L) },
      thumbnailGenerator: ImageThumbnailGenerator = successfulThumbnailGenerator(),
  ): ImageService {
    return ImageService(
        storageClients = storageClients,
        storageProfiles = storageProfiles,
        objectKeyGenerator = { "images/test/original.png" },
        imageInputValidator = imageInputValidator,
        metadataReader = metadataReader,
        thumbnailGenerator = thumbnailGenerator,
    )
  }

  private fun successfulThumbnailGenerator(): ImageThumbnailGenerator {
    return ImageThumbnailGenerator { _, _, objectKey, _ ->
      val temp = File.createTempFile("atomic-storage-thumb-", ".webp")
      temp.writeBytes(byteArrayOf(9, 9, 9))
      GeneratedThumbnail(
          objectKey = "${objectKey}_thumb.webp",
          file = temp,
          metadata = ImageMetadata(width = 30, height = 20, size = temp.length()),
      )
    }
  }

  private fun failingThumbnailGenerator(): ImageThumbnailGenerator {
    return ImageThumbnailGenerator { _, _, _, _ -> throw IllegalStateException("thumbnail failed") }
  }

  private fun defaultImageInputValidator(): ImageInputValidator {
    return ImageInputValidator { _, _ ->
      ValidatedImageInput(
          extension = "png",
          contentType = "image/png",
          detectedFormat = "PNG",
      )
    }
  }

  private fun tempFile(): File {
    return File.createTempFile("atomic-storage-source-", ".png").apply {
      writeBytes(byteArrayOf(1, 2, 3))
    }
  }

  private class CapturingStorageClient(
      private val failPutObject: (String) -> Boolean = { false },
  ) : StorageClient {
    val putRequests = mutableListOf<PutObjectRequest>()
    val deletedObjectKeys = mutableListOf<String>()

    override fun putObject(request: PutObjectRequest) {
      putRequests.add(request)
      if (failPutObject(request.objectKey)) {
        throw IllegalStateException("thumbnail upload failed")
      }
    }

    override fun deleteObject(objectKey: String) {
      deletedObjectKeys.add(objectKey)
    }
  }

  private class NonClosingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed: Boolean = false

    override fun close() {
      closed = true
    }
  }
}
