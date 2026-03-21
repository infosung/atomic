package com.infosung.atomic.app.storage

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.infosung.atomic.app.storage.adapter.out.storage.ImageServiceStoragePortAdapter
import com.infosung.atomic.app.storage.application.service.AppImageDeleteRecoveryService
import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.storage.PutObjectRequest
import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.ImageMetadata
import com.infosung.atomic.storage.image.ImageService
import com.infosung.atomic.storage.image.spi.GeneratedThumbnail
import com.infosung.atomic.storage.image.spi.ImageInputValidator
import com.infosung.atomic.storage.image.spi.ImageMetadataReader
import com.infosung.atomic.storage.image.spi.ImageObjectKeyGenerator
import com.infosung.atomic.storage.image.spi.ImageThumbnailGenerator
import com.infosung.atomic.storage.image.spi.ValidatedImageInput
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class AppImageDeleteRecoveryServiceTest {
  @Test
  fun `inspectDeletePendingImages should return pending count and oldest createdAt`() {
    val oldest =
        newDeletePendingImage(
            fileName = "images/test/oldest.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        )
    val newest =
        newDeletePendingImage(
            fileName = "images/test/newest.png",
            createdAt = LocalDateTime.of(2024, 1, 2, 0, 0, 0),
        )
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = FakeRecoveryImageMetadataPort(listOf(newest, oldest)),
            imageObjectStoragePort = newImageObjectStoragePort(CapturingStorageClient()),
        )

    val snapshot = recoveryService.inspectDeletePendingImages()

    assertEquals(2, snapshot.pendingCount)
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), snapshot.oldestPendingCreatedAt)
  }

  @Test
  fun `inspectDeletePendingImages should log deterministic oldest pending age with fixed clock`() {
    val oldest =
        newDeletePendingImage(
            fileName = "images/test/oldest.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        )
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = FakeRecoveryImageMetadataPort(listOf(oldest)),
            imageObjectStoragePort = newImageObjectStoragePort(CapturingStorageClient()),
            clock = Clock.fixed(Instant.parse("2024-01-01T00:00:10Z"), ZoneOffset.UTC),
        )

    withListAppender(AppImageDeleteRecoveryService::class.java, Level.DEBUG) { events ->
      recoveryService.inspectDeletePendingImages()

      val logs = events.map { it.formattedMessage }
      assertTrue(logs.any { it.contains("oldestPendingAgeSeconds=10") })
    }
  }

  @Test
  fun `recoverDeletePendingImages should purge recovered rows up to limit`() {
    val image1 = newDeletePendingImage(createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0))
    val image2 = newDeletePendingImage(createdAt = LocalDateTime.of(2024, 1, 1, 0, 1, 0))
    val image3 = newDeletePendingImage(createdAt = LocalDateTime.of(2024, 1, 1, 0, 2, 0))
    val txPort = FakeRecoveryImageMetadataPort(listOf(image1, image2, image3))
    val storageClient = CapturingStorageClient()
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = txPort,
            imageObjectStoragePort = newImageObjectStoragePort(storageClient),
        )

    val result = recoveryService.recoverDeletePendingImages(limit = 2)

    assertEquals(2, result.scannedCount)
    assertEquals(2, result.recoveredCount)
    assertEquals(0, result.failedCount)
    assertEquals(1, result.remainingPendingCount)
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 2, 0), result.oldestPendingCreatedAt)
    assertEquals(1, txPort.claimedBatches.size)
    assertEquals(listOf(requireNotNull(image1.id), requireNotNull(image2.id)), txPort.purgedIds)
    assertEquals(listOf(requireNotNull(image3.id)), txPort.remainingIds())
  }

  @Test
  fun `recoverDeletePendingImages should continue after one item fails`() {
    val image1 =
        newDeletePendingImage(
            fileName = "images/test/success-1.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        )
    val image2 =
        newDeletePendingImage(
            fileName = "images/test/fail.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 1, 0),
        )
    val image3 =
        newDeletePendingImage(
            fileName = "images/test/success-2.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 2, 0),
        )
    val txPort = FakeRecoveryImageMetadataPort(listOf(image1, image2, image3))
    val storageClient =
        CapturingStorageClient(
            failDeleteObject = { key -> key == "images/test/fail.png" },
        )
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = txPort,
            imageObjectStoragePort = newImageObjectStoragePort(storageClient),
        )

    val result = recoveryService.recoverDeletePendingImages(limit = 10)

    assertEquals(3, result.scannedCount)
    assertEquals(2, result.recoveredCount)
    assertEquals(1, result.failedCount)
    assertEquals(1, result.remainingPendingCount)
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 1, 0), result.oldestPendingCreatedAt)
    assertEquals(listOf(requireNotNull(image2.id)), txPort.releasedIds)
    assertEquals(
        listOf(requireNotNull(image1.id), requireNotNull(image3.id)),
        txPort.purgedIds,
    )
    assertEquals(listOf(requireNotNull(image2.id)), txPort.remainingIds())
  }

  @Test
  fun `recoverDeletePendingImages should reject non positive limit`() {
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = FakeRecoveryImageMetadataPort(emptyList()),
            imageObjectStoragePort = newImageObjectStoragePort(CapturingStorageClient()),
        )

    val exception =
        assertFailsWith<IllegalArgumentException> {
          recoveryService.recoverDeletePendingImages(limit = 0)
        }

    assertEquals("limit must be greater than zero.", exception.message)
  }

  @Test
  fun `recoverDeletePendingImages should skip rows already claimed by another batch`() {
    val claimedElsewhere =
        newDeletePendingImage(
            fileName = "images/test/claimed-elsewhere.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        )
    val claimable =
        newDeletePendingImage(
            fileName = "images/test/claimable.png",
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 1, 0),
        )
    val txPort =
        FakeRecoveryImageMetadataPort(
            initialImages = listOf(claimedElsewhere, claimable),
            externallyClaimedIds = setOf(requireNotNull(claimedElsewhere.id)),
        )
    val recoveryService =
        AppImageDeleteRecoveryService(
            imageMetadataPort = txPort,
            imageObjectStoragePort = newImageObjectStoragePort(CapturingStorageClient()),
        )

    val result = recoveryService.recoverDeletePendingImages(limit = 10)

    assertEquals(1, result.scannedCount)
    assertEquals(1, result.recoveredCount)
    assertEquals(0, result.failedCount)
    assertEquals(1, result.remainingPendingCount)
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), result.oldestPendingCreatedAt)
    assertEquals(listOf(requireNotNull(claimable.id)), txPort.purgedIds)
    assertEquals(listOf(requireNotNull(claimedElsewhere.id)), txPort.remainingIds())
  }

  private fun newImageObjectStoragePort(
      storageClient: StorageClient,
  ): ImageServiceStoragePortAdapter {
    return ImageServiceStoragePortAdapter(
        imageService = createImageService(storageClient = storageClient, storageType = "S3"),
        storageClients = mapOf("S3" to storageClient),
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

  private fun newDeletePendingImage(
      fileName: String = "images/test/original.png",
      thumbnailFileName: String = "images/test/original_thumb.webp",
      createdAt: LocalDateTime = LocalDateTime.now(),
  ): StoredImage {
    return StoredImage(
        id = UUID.randomUUID(),
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = StoredImage.STATUS_DELETE_PENDING,
        storageType = "S3",
        fileName = fileName,
        thumbnailFileName = thumbnailFileName,
        url = "https://cdn.example.com/$fileName",
        thumbnailUrl = "https://cdn.example.com/$thumbnailFileName",
        fileSize = 123,
        createdAt = createdAt,
    )
  }

  private class FakeRecoveryImageMetadataPort(
      initialImages: List<StoredImage>,
      private val externallyClaimedIds: Set<UUID> = emptySet(),
  ) : com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort {
    private val pendingImages: MutableList<StoredImage> = initialImages.toMutableList()
    val purgedIds: MutableList<UUID> = mutableListOf()
    val releasedIds: MutableList<UUID> = mutableListOf()
    val claimedBatches: MutableList<List<UUID>> = mutableListOf()

    override fun findByIdOrThrow(imageId: UUID, rawImageId: String): StoredImage {
      throw UnsupportedOperationException("not needed in this test")
    }

    override fun save(image: StoredImage): StoredImage {
      throw UnsupportedOperationException("not needed in this test")
    }

    override fun markDeletePending(image: StoredImage): StoredImage {
      throw UnsupportedOperationException("not needed in this test")
    }

    override fun purgeDeletePending(image: StoredImage) {
      val imageId = requireNotNull(image.id)
      purgedIds += imageId
      pendingImages.removeIf { it.id == imageId }
    }

    override fun inspectDeletePendingImages(): ImageDeletePendingSnapshot {
      val oldestPendingCreatedAt = pendingImages.minByOrNull { it.createdAt }?.createdAt
      return ImageDeletePendingSnapshot(
          pendingCount = pendingImages.size.toLong(),
          oldestPendingCreatedAt = oldestPendingCreatedAt,
      )
    }

    override fun claimDeletePending(
        limit: Int,
        claimToken: String,
        claimedAt: LocalDateTime,
    ): List<StoredImage> {
      val claimed =
          pendingImages
              .asSequence()
              .filter { image -> requireNotNull(image.id) !in externallyClaimedIds }
              .take(limit)
              .toList()
      claimedBatches += claimed.map { requireNotNull(it.id) }
      return claimed
    }

    override fun releaseDeleteRecoveryClaim(imageId: UUID, claimToken: String) {
      releasedIds += imageId
    }

    fun remainingIds(): List<UUID> = pendingImages.mapNotNull { it.id }
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

  private fun <T> withListAppender(
      loggerClass: Class<*>,
      level: Level,
      block: (events: List<ILoggingEvent>) -> T,
  ): T {
    val logger = LoggerFactory.getLogger(loggerClass) as Logger
    val previousLevel = logger.level
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    logger.addAppender(appender)
    logger.level = level
    return try {
      block(appender.list)
    } finally {
      logger.detachAppender(appender)
      logger.level = previousLevel
    }
  }
}
