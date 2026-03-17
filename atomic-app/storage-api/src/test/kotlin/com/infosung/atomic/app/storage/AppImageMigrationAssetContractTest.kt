package com.infosung.atomic.app.storage

import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/postgresql/test/drop_image.sql,classpath:META-INF/atomic/sql/postgresql/image.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppImageEntityTxService::class)
@Testcontainers(disabledWithoutDocker = true)
class AppImageMigrationAssetContractTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService
  @Autowired private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `official image sql asset should support save find and delete`() {
    val saved = imageEntityTxService.save(newEntity())
    val imageId = requireNotNull(saved.id)

    val loaded = imageEntityTxService.findByIdOrThrow(imageId, imageId.toString())
    assertNotNull(loaded.id)
    assertEquals(saved.fileName, loaded.fileName)
    assertEquals(saved.storageType, loaded.storageType)

    imageEntityTxService.delete(saved)

    val remaining =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM image WHERE id = ?",
            Long::class.java,
            imageId.toString(),
        )

    assertEquals(0L, remaining)
  }

  @Test
  fun `official image sql asset should create documented index`() {
    val indexes =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'image'
            """
                .trimIndent(),
            String::class.java,
        )

    assertTrue(indexes.contains("idx_image_service_storage"))
    assertTrue(indexes.contains("idx_image_status_created_at"))
    assertTrue(indexes.contains("idx_image_status_claim_created_at"))
  }

  @Test
  fun `official image sql asset should support delete pending snapshot queries`() {
    imageEntityTxService.save(
        newEntity(
            fileName = "images/older/original.png",
            status = ImageEntity.STATUS_DELETE_PENDING,
            createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        ),
    )
    imageEntityTxService.save(
        newEntity(
            fileName = "images/newer/original.png",
            status = ImageEntity.STATUS_DELETE_PENDING,
            createdAt = LocalDateTime.of(2024, 1, 2, 0, 0, 0),
        ),
    )

    val snapshot = imageEntityTxService.inspectDeletePendingImages()

    assertEquals(2L, snapshot.pendingCount)
    assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), snapshot.oldestPendingCreatedAt)
  }

  @Test
  fun `official image sql asset should support delete pending claim query`() {
    val oldest =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/claim/oldest.png",
                status = ImageEntity.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            ),
        )
    val newest =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/claim/newest.png",
                status = ImageEntity.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2024, 1, 2, 0, 0, 0),
            ),
        )

    val firstClaim =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "batch-1",
            claimedAt = LocalDateTime.of(2024, 1, 3, 0, 0, 0),
        )
    val secondClaim =
        imageEntityTxService.claimDeletePending(
            limit = 2,
            claimToken = "batch-2",
            claimedAt = LocalDateTime.of(2024, 1, 3, 0, 1, 0),
        )

    assertEquals(listOf(requireNotNull(oldest.id)), firstClaim.mapNotNull { it.id })
    assertEquals(listOf(requireNotNull(newest.id)), secondClaim.mapNotNull { it.id })
  }

  @Test
  fun `official image sql asset should reclaim stale delete pending claim`() {
    val image =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/claim/stale.png",
                status = ImageEntity.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2024, 1, 1, 0, 0, 0),
            ),
        )
    jdbcTemplate.update(
        """
        UPDATE image
        SET delete_recovery_claim_token = ?,
            delete_recovery_claimed_at = ?
        WHERE id = ?
        """
            .trimIndent(),
        "stale-batch",
        LocalDateTime.of(2024, 1, 1, 0, 0, 0),
        requireNotNull(image.id).toString(),
    )

    val reclaimed =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "fresh-batch",
            claimedAt = LocalDateTime.of(2024, 1, 1, 0, 16, 0),
        )

    assertEquals(listOf(requireNotNull(image.id)), reclaimed.mapNotNull { it.id })
  }

  @Test
  fun `official image sql asset should support delete pending claim columns`() {
    val columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'image'
            """
                .trimIndent(),
            String::class.java,
        )

    assertTrue(columns.contains("delete_recovery_claim_token"))
    assertTrue(columns.contains("delete_recovery_claimed_at"))
  }

  @Test
  fun `official image sql asset should support long external strings`() {
    val saved =
        imageEntityTxService.save(
            newEntity(
                fileName = longValue("original-", 640),
                thumbnailFileName = longValue("thumbnail-", 620),
                url = "https://cdn.example.com/${longValue("path-", 900)}",
                thumbnailUrl = "https://cdn.example.com/${longValue("thumb-", 880)}",
            ),
        )

    val imageId = requireNotNull(saved.id)
    val loaded = imageEntityTxService.findByIdOrThrow(imageId, imageId.toString())

    assertEquals(saved.fileName, loaded.fileName)
    assertEquals(saved.thumbnailFileName, loaded.thumbnailFileName)
    assertEquals(saved.url, loaded.url)
    assertEquals(saved.thumbnailUrl, loaded.thumbnailUrl)
  }

  private fun newEntity(
      fileName: String? = null,
      thumbnailFileName: String? = null,
      url: String? = null,
      thumbnailUrl: String? = null,
      status: String = ImageEntity.STATUS_ACTIVE,
      createdAt: LocalDateTime = LocalDateTime.now(),
  ): ImageEntity {
    val suffix = UUID.randomUUID().toString().take(8)
    val defaultObjectKey = "images/$suffix/original.png"
    val defaultThumbnailKey = "images/$suffix/original_thumb.webp"
    return ImageEntity(
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

  private fun longValue(prefix: String, totalLength: Int): String {
    return prefix + "x".repeat((totalLength - prefix.length).coerceAtLeast(0))
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ImageEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
  class TestConfiguration

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_image_migration_asset_contract_${UUID.randomUUID()}")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
    }
  }
}
