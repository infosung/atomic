package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.application.port.out.ImageMetadataPort
import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot
import com.infosung.atomic.app.storage.domain.StoredImage
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
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [AppStorageControllerBootSmokeContractTest.TestApplication::class],
    properties =
        [
            "atomic.app.image.enabled=true",
            "atomic.app.image.endpoint-path=/test/api/storage/image",
        ],
)
@AutoConfigureMockMvc
class AppStorageControllerBootSmokeContractTest {
  @jakarta.annotation.Resource private lateinit var mockMvc: MockMvc

  @Test
  fun `boot mvc should expose upload endpoint through umbrella auto configuration`() {
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    mockMvc
        .perform(
            multipart("/test/api/storage/image/svc/s3")
                .file(MockMultipartFile("file", "image.png", "image/png", byteArrayOf(1, 2, 3)))
                .queryParam("quality", "0.8"),
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.id").value(imageId.toString()))
        .andExpect(jsonPath("$.data.storageType").value("svc:s3"))
        .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/images/test/original.png"))
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName =
          [
              "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
              "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
              "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
          ],
  )
  @EnableConfigurationProperties(AtomicAppImageProperties::class)
  class TestApplication {
    @Bean("storageClients")
    fun storageClients(): Map<String, StorageClient> = mapOf("svc:s3" to NoopStorageClient())

    @Bean
    fun storageProfiles(): Map<String, StorageProfile> =
        mapOf(
            "svc:s3" to
                StorageProfile(
                    bucket = "bucket",
                    cdn = "https://cdn.example.com",
                ),
        )

    @Bean
    fun imageService(
        storageClients: Map<String, StorageClient>,
        storageProfiles: Map<String, StorageProfile>,
    ): ImageService {
      return ImageService(
          storageClients = storageClients,
          storageProfiles = storageProfiles,
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
                  ImageMetadata(width = 320, height = 240, size = 4567)
                } else {
                  ImageMetadata(width = 640, height = 480, size = 12345)
                }
              },
          thumbnailGenerator =
              ImageThumbnailGenerator { _, _, sourceObjectKey, _ ->
                val thumbnailFile =
                    kotlin.io.path.createTempFile("app-storage-boot-smoke-", ".webp").toFile()
                thumbnailFile.writeBytes(byteArrayOf(1, 2, 3))
                GeneratedThumbnail(
                    objectKey = "${sourceObjectKey}_thumb.webp",
                    file = thumbnailFile,
                    metadata = ImageMetadata(width = 320, height = 240, size = 4567),
                )
              },
      )
    }

    @Bean fun imageMetadataPort(): ImageMetadataPort = BootSmokeImageMetadataPort()
  }

  private class NoopStorageClient : StorageClient {
    override fun putObject(request: PutObjectRequest) = Unit

    override fun deleteObject(objectKey: String) = Unit
  }

  private class BootSmokeImageMetadataPort : ImageMetadataPort {
    override fun findByIdOrThrow(
        imageId: UUID,
        rawImageId: String,
    ): StoredImage = savedImage(imageId)

    override fun save(image: StoredImage): StoredImage = savedImage()

    override fun markDeletePending(image: StoredImage): StoredImage = image

    override fun purgeDeletePending(image: StoredImage) = Unit

    override fun inspectDeletePendingImages(): ImageDeletePendingSnapshot =
        ImageDeletePendingSnapshot(pendingCount = 0, oldestPendingCreatedAt = null)

    override fun claimDeletePending(
        limit: Int,
        claimToken: String,
        claimedAt: LocalDateTime,
    ): List<StoredImage> = emptyList()

    override fun releaseDeleteRecoveryClaim(
        imageId: UUID,
        claimToken: String,
    ) = Unit

    private fun savedImage(
        id: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    ): StoredImage {
      return StoredImage(
          id = id,
          bucket = "bucket",
          serviceName = "svc",
          storageService = "s3",
          storageType = "svc:s3",
          fileName = "images/test/original.png",
          thumbnailFileName = "images/test/original.png_thumb.webp",
          url = "https://cdn.example.com/images/test/original.png",
          thumbnailUrl = "https://cdn.example.com/images/test/original.png_thumb.webp",
          width = 640,
          height = 480,
          fileSize = 12345,
          thumbnailWidth = 320,
          thumbnailHeight = 240,
          thumbnailFileSize = 4567,
          createdAt = LocalDateTime.of(2026, 3, 14, 10, 0, 0),
      )
    }
  }
}
