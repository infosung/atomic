package com.infosung.atomic.app.storage

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
  ): ImageEntity {
    val suffix = UUID.randomUUID().toString().take(8)
    val defaultObjectKey = "images/$suffix/original.png"
    val defaultThumbnailKey = "images/$suffix/original_thumb.webp"
    return ImageEntity(
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        storageType = "S3",
        fileName = fileName ?: defaultObjectKey,
        thumbnailFileName = thumbnailFileName ?: defaultThumbnailKey,
        url = url ?: "https://cdn.example.com/$defaultObjectKey",
        thumbnailUrl = thumbnailUrl ?: "https://cdn.example.com/$defaultThumbnailKey",
        fileSize = 123,
        thumbnailFileSize = 45,
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
