package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import java.time.LocalDateTime
import java.util.UUID
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
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
    properties = ["atomic.app.image.endpoint-path=/test/api/storage/image"],
)
@AutoConfigureMockMvc
class AppStorageControllerBootSmokeContractTest {
  @jakarta.annotation.Resource private lateinit var mockMvc: MockMvc

  @Test
  fun `boot mvc should expose upload endpoint and serialize response envelope`() {
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
        .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/images/test/original.png"))
  }

  @Test
  fun `boot mvc should keep documented 400 status without custom exception advice`() {
    mockMvc
        .perform(
            multipart("/test/api/storage/image/svc/s3")
                .file(MockMultipartFile("file", "image.png", "image/png", byteArrayOf(1, 2, 3)))
                .queryParam("quality", "0.05"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("quality must be in range 0.1..1.0"))
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
    @Bean
    fun appImageApiService(): AppImageApiService =
        mock(AppImageApiService::class.java) { invocation ->
          when (invocation.method.name) {
            "uploadImage" ->
                if (invocation.arguments[3] == 0.05) {
                  throw HttpStatusException(
                      status = 400, message = "quality must be in range 0.1..1.0")
                } else {
                  ImageEntity(
                      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                      bucket = "bucket",
                      serviceName = "svc",
                      storageService = "s3",
                      storageType = "S3",
                      fileName = "images/test/original.png",
                      thumbnailFileName = "images/test/original_thumb.webp",
                      url = "https://cdn.example.com/images/test/original.png",
                      thumbnailUrl = "https://cdn.example.com/images/test/original_thumb.webp",
                      fileSize = 12,
                      createdAt = LocalDateTime.of(2026, 3, 14, 10, 0, 0),
                  )
                }
            else -> null
          }
        }

    @Bean
    fun appStorageController(
        appImageApiService: AppImageApiService,
        properties: AtomicAppImageProperties,
    ): AppStorageController {
      return AppStorageController(appImageApiService = appImageApiService, properties = properties)
    }

    @Bean
    fun appStorageHttpExceptionHandler(): AppStorageHttpExceptionHandler {
      return AppStorageHttpExceptionHandler()
    }
  }
}
