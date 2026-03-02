package com.infosung.atomic.app.storage

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppImageEntityTxService::class)
@Testcontainers(disabledWithoutDocker = true)
class AppImageEntityTxServiceContainerTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService

  @Test
  fun `save and find should persist metadata on postgres`() {
    val saved = imageEntityTxService.save(newEntity())
    val imageId = requireNotNull(saved.id)
    assertNotNull(imageId)
    val loaded = imageEntityTxService.findByIdOrThrow(imageId, imageId.toString())
    assertEquals(saved.id, loaded.id)
    assertEquals(saved.fileName, loaded.fileName)
    assertEquals(saved.thumbnailFileName, loaded.thumbnailFileName)
    assertEquals(saved.storageType, loaded.storageType)
  }

  @Test
  fun `delete should remove metadata row on postgres`() {
    val saved = imageEntityTxService.save(newEntity())
    val imageId = requireNotNull(saved.id)

    imageEntityTxService.delete(saved)

    val exception =
        assertThrows<IllegalArgumentException> {
          imageEntityTxService.findByIdOrThrow(imageId, imageId.toString())
        }
    assertTrue(exception.message?.contains("image not found") == true)
  }

  private fun newEntity(): ImageEntity {
    val suffix = UUID.randomUUID().toString().take(8)
    val objectKey = "images/$suffix/original.png"
    val thumbnailKey = "images/$suffix/original_thumb.webp"
    return ImageEntity(
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        storageType = "S3",
        fileName = objectKey,
        thumbnailFileName = thumbnailKey,
        url = "https://cdn.example.com/$objectKey",
        thumbnailUrl = "https://cdn.example.com/$thumbnailKey",
        fileSize = 123,
        thumbnailFileSize = 45,
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
    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
      registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
    }
  }
}
