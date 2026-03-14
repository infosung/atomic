package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class AppStorageControllerHttpContractTest {
  @Test
  fun `upload endpoint should return documented response envelope`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties()
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val imageEntity = sampleImageEntity()

    `when`(service.uploadImage("svc", "S3", multipartFile, 0.75, null)).thenReturn(imageEntity)

    mockMvc
        .perform(
            multipart("/api/v1/storage/image/svc/S3")
                .file(multipartFile)
                .param("quality", "0.75"),
        )
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))
        .andExpect(jsonPath("$.data.id").value(imageEntity.id.toString()))
        .andExpect(jsonPath("$.data.bucket").value("bucket"))
        .andExpect(jsonPath("$.data.serviceName").value("svc"))
        .andExpect(jsonPath("$.data.storageService").value("S3"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.uploaderId").value("member-100"))
        .andExpect(jsonPath("$.data.storageType").value("svc:S3"))
        .andExpect(jsonPath("$.data.fileName").value("images/test/original.png"))
        .andExpect(jsonPath("$.data.thumbnailFileName").value("images/test/original_thumb.webp"))
        .andExpect(jsonPath("$.data.url").value("https://cdn/images/test/original.png"))
        .andExpect(
            jsonPath("$.data.thumbnailUrl").value("https://cdn/images/test/original_thumb.webp"),
        )
        .andExpect(jsonPath("$.data.width").value(320))
        .andExpect(jsonPath("$.data.height").value(180))
        .andExpect(jsonPath("$.data.fileSize").value(1024))
        .andExpect(jsonPath("$.data.thumbnailWidth").value(160))
        .andExpect(jsonPath("$.data.thumbnailHeight").value(90))
        .andExpect(jsonPath("$.data.thumbnailFileSize").value(256))

    verify(service).uploadImage("svc", "S3", multipartFile, 0.75, null)
  }

  @Test
  fun `configured endpoint path and uploader parameter name should be honored`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          endpointPath = "/internal/api/storage/image"
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val imageEntity = sampleImageEntity()

    `when`(service.uploadImage("svc", "S3", multipartFile, 1.0, "member-100"))
        .thenReturn(imageEntity)

    mockMvc
        .perform(
            multipart("/internal/api/storage/image/svc/S3")
                .file(multipartFile)
                .param("memberId", "member-100"),
        )
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.uploaderId").value("member-100"))

    mockMvc
        .perform(
            multipart("/api/v1/storage/image/svc/S3")
                .file(MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray()))
                .param("memberId", "member-100"),
        )
        .andExpect(status().isNotFound)

    verify(service).uploadImage("svc", "S3", multipartFile, 1.0, "member-100")
  }

  @Test
  fun `default quality should be used when request omits quality parameter`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties().apply { defaultQuality = 0.85 }
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    `when`(service.uploadImage("svc", "S3", multipartFile, 0.85, null)).thenReturn(sampleImageEntity())

    mockMvc
        .perform(multipart("/api/v1/storage/image/svc/S3").file(multipartFile))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.code").value("OK"))

    verify(service).uploadImage("svc", "S3", multipartFile, 0.85, null)
  }

  @Test
  fun `missing uploader parameter should return documented 400 error envelope`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    mockMvc
        .perform(multipart("/api/v1/storage/image/svc/S3").file(multipartFile))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(
            jsonPath("$.message")
                .value("memberId is required when uploader parameter tracking is enabled."),
        )

    verifyNoInteractions(service)
  }

  @Test
  fun `quality validation failure should return documented 400 error envelope`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties()
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    `when`(service.uploadImage("svc", "S3", multipartFile, 0.05, null))
        .thenThrow(HttpStatusException(status = 400, message = "quality must be in range 0.1..1.0"))

    mockMvc
        .perform(
            multipart("/api/v1/storage/image/svc/S3")
                .file(multipartFile)
                .param("quality", "0.05"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("quality must be in range 0.1..1.0"))
  }

  @Test
  fun `delete endpoint should return success envelope`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties()
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))

    verify(service).deleteImage("svc", "S3", imageId, null)
  }

  @Test
  fun `delete endpoint should return documented 400 error envelope when stored mapping is unavailable`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties()
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    `when`(service.deleteImage("svc", "S3", imageId, null))
        .thenThrow(
            HttpStatusException(
                status = 400,
                message = "stored storageType is unavailable for image delete: UNKNOWN",
            ),
        )

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(
            jsonPath("$.message")
                .value("stored storageType is unavailable for image delete: UNKNOWN"),
        )

    verify(service).deleteImage("svc", "S3", imageId, null)
  }

  @Test
  fun `delete should require uploader parameter when tracking is enabled`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val mockMvc = newMockMvc(controller = controller, endpointPath = properties.endpointPath)
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(
            jsonPath("$.message")
                .value("memberId is required when uploader parameter tracking is enabled."),
        )

    verifyNoInteractions(service)
  }

  private fun newMockMvc(
      controller: AppStorageController,
      endpointPath: String,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(TestHttpStatusExceptionHandler())
        .addPlaceholderValue("atomic.app.image.endpoint-path", endpointPath)
        .build()
  }

  private fun sampleImageEntity(): ImageEntity {
    return ImageEntity(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = "ACTIVE",
        uploaderId = "member-100",
        storageType = "svc:S3",
        fileName = "images/test/original.png",
        thumbnailFileName = "images/test/original_thumb.webp",
        url = "https://cdn/images/test/original.png",
        thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
        width = 320,
        height = 180,
        fileSize = 1024,
        thumbnailWidth = 160,
        thumbnailHeight = 90,
        thumbnailFileSize = 256,
        createdAt = LocalDateTime.of(2026, 3, 14, 9, 30, 0),
    )
  }

  @RestControllerAdvice
  private class TestHttpStatusExceptionHandler {
    @ExceptionHandler(HttpStatusException::class)
    fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
      return ResponseEntity.status(e.status).body(BaseResponse.error(e))
    }
  }
}
