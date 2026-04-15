package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository
import com.infosung.atomic.app.storage.domain.StoredImage
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer

@EnabledIfEnvironmentVariable(named = "ATOMIC_RUN_ORACLE_COMPATIBILITY", matches = "true")
@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/oracle/image.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppImageEntityTxService::class)
@Testcontainers(disabledWithoutDocker = true)
class AppImageOracleCompatibilityContractTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService

  @Test
  fun `oracle asset should support save find and claim on jpa image path`() {
    val saved =
        imageEntityTxService.save(
            newEntity(
                fileName = "images/oracle/original.png",
                status = StoredImage.STATUS_DELETE_PENDING,
                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
            ),
        )

    val loaded = imageEntityTxService.findByIdOrThrow(requireNotNull(saved.id), saved.id.toString())
    assertNotNull(loaded.id)
    assertEquals(saved.fileName, loaded.fileName)

    val claimed =
        imageEntityTxService.claimDeletePending(
            limit = 1,
            claimToken = "oracle-claim",
            claimedAt = LocalDateTime.of(2026, 1, 1, 0, 16, 0),
        )

    assertEquals(listOf(requireNotNull(saved.id)), claimed.mapNotNull { it.id })
  }

  private fun newEntity(
      fileName: String,
      status: String,
      createdAt: LocalDateTime,
  ): StoredImage {
    val suffix = UUID.randomUUID().toString().take(8)
    val defaultThumbnailKey = "images/$suffix/original_thumb.webp"
    return StoredImage(
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = status,
        storageType = "S3",
        fileName = fileName,
        thumbnailFileName = defaultThumbnailKey,
        url = "https://cdn.example.com/$fileName",
        thumbnailUrl = "https://cdn.example.com/$defaultThumbnailKey",
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

  companion object {
    @Container
    @JvmStatic
    private val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", oracle::getJdbcUrl)
      registry.add("spring.datasource.username", oracle::getUsername)
      registry.add("spring.datasource.password", oracle::getPassword)
    }
  }
}
