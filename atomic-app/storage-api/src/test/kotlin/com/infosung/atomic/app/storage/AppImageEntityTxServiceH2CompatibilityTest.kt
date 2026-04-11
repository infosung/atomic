package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository
import com.infosung.atomic.app.storage.domain.StoredImage
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.globally_quoted_identifiers=false",
        ],
)
@Import(AppImageEntityTxService::class)
class AppImageEntityTxServiceH2CompatibilityTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService

  @Test
  fun `claimDeletePending should work on h2 with portable persistence path`() {
    val oldest =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/h2/oldest.png",
                status = StoredImage.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
            ),
        )
    val newest =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/h2/newest.png",
                status = StoredImage.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2026, 1, 2, 0, 0, 0),
            ),
        )

    val firstClaim =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "h2-claim-1",
            claimedAt = LocalDateTime.of(2026, 1, 3, 0, 0, 0),
        )
    val secondClaim =
        imageEntityTxService.claimDeletePending(
            limit = 2,
            claimToken = "h2-claim-2",
            claimedAt = LocalDateTime.of(2026, 1, 3, 0, 1, 0),
        )

    assertEquals(listOf(requireNotNull(oldest.id)), firstClaim.mapNotNull { it.id })
    assertEquals(listOf(requireNotNull(newest.id)), secondClaim.mapNotNull { it.id })
  }

  @Test
  fun `claimDeletePending should reclaim stale claim on h2`() {
    val image =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/h2/stale.png",
                status = StoredImage.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
            ),
        )

    imageEntityTxService.claimDeletePending(
        limit = 1,
        claimToken = "stale-claim",
        claimedAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
    )

    val reclaimed =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "fresh-claim",
            claimedAt = LocalDateTime.of(2026, 1, 1, 0, 16, 0),
        )

    assertEquals(listOf(requireNotNull(image.id)), reclaimed.mapNotNull { it.id })
  }

  @Test
  fun `releaseDeleteRecoveryClaim should clear portable claim state on h2`() {
    val image =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/h2/release.png",
                status = StoredImage.STATUS_DELETE_PENDING,
            ),
        )

    val claimToken = "claim-release"
    val claimed =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = claimToken,
            claimedAt = LocalDateTime.of(2026, 1, 3, 0, 0, 0),
        )
    assertTrue(claimed.isNotEmpty())

    imageEntityTxService.releaseDeleteRecoveryClaim(requireNotNull(image.id), claimToken)

    val reclaimed =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "claim-release-2",
            claimedAt = LocalDateTime.of(2026, 1, 3, 0, 1, 0),
        )
    assertEquals(listOf(requireNotNull(image.id)), reclaimed.mapNotNull { it.id })
  }

  private fun newEntity(
      fileName: String? = null,
      thumbnailFileName: String? = null,
      url: String? = null,
      thumbnailUrl: String? = null,
      status: String = StoredImage.STATUS_ACTIVE,
      createdAt: LocalDateTime = LocalDateTime.now(),
  ): StoredImage {
    val suffix = UUID.randomUUID().toString().take(8)
    val defaultObjectKey = "images/$suffix/original.png"
    val defaultThumbnailKey = "images/$suffix/original_thumb.webp"
    return StoredImage(
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = status,
        storageType = "S3",
        fileName = fileName ?: defaultObjectKey,
        thumbnailFileName = thumbnailFileName ?: defaultThumbnailKey,
        url = url ?: "https://cdn.example.com/$defaultObjectKey",
        thumbnailUrl = thumbnailUrl ?: "https://cdn.example.com/$defaultThumbnailKey",
        fileSize = 123,
        thumbnailFileSize = 45,
        createdAt = createdAt,
    )
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ImageEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
  class TestConfiguration
}
