package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageController
import com.infosung.atomic.app.storage.application.exception.ImageNotFoundException
import com.infosung.atomic.app.storage.application.exception.ImageOwnershipMismatchException
import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.exception.StorageConfigurationException
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.app.storage.domain.StoredImage
import com.infosung.atomic.spring.web.exception.AtomicHttpExceptionHandler
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AppStorageControllerHttpContractTest {
  @Test
  fun `upload endpoint should return documented response envelope`() {
    val uploadUseCase = RecordingUploadUseCase(sampleStoredImage())
    val controller =
        AppStorageController(
            uploadUseCase,
            RecordingDeleteUseCase(),
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val storedImage = sampleStoredImage()
    uploadUseCase.result = storedImage

    mockMvc
        .perform(
            multipart("/api/v1/storage/image/svc/S3").file(multipartFile).param("quality", "0.75"),
        )
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))
        .andExpect(jsonPath("$.data.id").value(requireNotNull(storedImage.id).toString()))
        .andExpect(jsonPath("$.data.bucket").value("bucket"))
        .andExpect(jsonPath("$.data.serviceName").value("svc"))
        .andExpect(jsonPath("$.data.storageService").value("S3"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.uploaderId").value("member-100"))
        .andExpect(jsonPath("$.data.storageType").value("S3"))
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

    assertEquals(0.75, uploadUseCase.lastCommand!!.quality)
  }

  @Test
  fun `configured endpoint path and uploader parameter name should be honored`() {
    val properties =
        AtomicAppImageProperties().apply {
          endpointPath = "/internal/api/storage/image"
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val uploadUseCase = RecordingUploadUseCase(sampleStoredImage())
    val controller =
        AppStorageController(
            uploadUseCase,
            RecordingDeleteUseCase(),
            properties,
        )
    val mockMvc = newMockMvc(controller, properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    mockMvc
        .perform(
            multipart("/internal/api/storage/image/svc/S3")
                .file(multipartFile)
                .param("memberId", "member-100"),
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.uploaderId").value("member-100"))
  }

  @Test
  fun `missing uploader parameter should return documented 400 error envelope`() {
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            RecordingDeleteUseCase(),
            properties,
        )
    val mockMvc = newMockMvc(controller, properties.endpointPath)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    mockMvc
        .perform(multipart("/api/v1/storage/image/svc/S3").file(multipartFile))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("STORAGE_UPLOADER_PARAMETER_REQUIRED"))
        .andExpect(
            jsonPath("$.message")
                .value("memberId is required when uploader parameter tracking is enabled."),
        )
  }

  @Test
  fun `quality validation failure should return documented 400 error envelope`() {
    val uploadUseCase =
        RecordingUploadUseCase(sampleStoredImage()).apply {
          uploadException =
              InvalidImageRequestException(
                  "quality must be in range 0.1..1.0",
                  errorCode =
                      com.infosung.atomic.app.storage.application.exception.StorageErrorCode
                          .STORAGE_IMAGE_QUALITY_INVALID,
              )
        }
    val controller =
        AppStorageController(
            uploadUseCase,
            RecordingDeleteUseCase(),
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    mockMvc
        .perform(
            multipart("/api/v1/storage/image/svc/S3").file(multipartFile).param("quality", "0.05"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("STORAGE_IMAGE_QUALITY_INVALID"))
        .andExpect(jsonPath("$.message").value("quality must be in range 0.1..1.0"))
  }

  @Test
  fun `delete invalid imageId should return refined 400 error envelope`() {
    val deleteUseCase =
        RecordingDeleteUseCase().apply {
          deleteException =
              InvalidImageRequestException(
                  "imageId must be a valid UUID.",
                  errorCode =
                      com.infosung.atomic.app.storage.application.exception.StorageErrorCode
                          .STORAGE_IMAGE_ID_INVALID,
              )
        }
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            deleteUseCase,
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", "not-a-uuid"))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("STORAGE_IMAGE_ID_INVALID"))
        .andExpect(jsonPath("$.message").value("imageId must be a valid UUID."))
  }

  @Test
  fun `delete endpoint should return success envelope`() {
    val deleteUseCase = RecordingDeleteUseCase()
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            deleteUseCase,
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))

    assertEquals(imageId, deleteUseCase.lastCommand!!.imageId)
  }

  @Test
  fun `delete endpoint should return documented 404 error envelope`() {
    val deleteUseCase =
        RecordingDeleteUseCase().apply {
          deleteException =
              ImageNotFoundException("image not found: 11111111-1111-1111-1111-111111111111")
        }
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            deleteUseCase,
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isNotFound)
        .andExpect(jsonPath("$.code").value("STORAGE_IMAGE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("image not found: $imageId"))
  }

  @Test
  fun `delete endpoint should return documented 403 error envelope`() {
    val deleteUseCase =
        RecordingDeleteUseCase().apply {
          deleteException =
              ImageOwnershipMismatchException(
                  "uploader parameter does not match uploaded image owner.")
        }
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            deleteUseCase,
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val imageId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString()

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3").param("imageId", imageId))
        .andExpect(status().isForbidden)
        .andExpect(jsonPath("$.code").value("STORAGE_IMAGE_OWNERSHIP_MISMATCH"))
        .andExpect(
            jsonPath("$.message").value("uploader parameter does not match uploaded image owner."),
        )
  }

  @Test
  fun `upload endpoint should preserve stable 500 code and mask message for configuration errors`() {
    val uploadUseCase =
        RecordingUploadUseCase(sampleStoredImage()).apply {
          uploadException =
              StorageConfigurationException(
                  "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
              )
        }
    val controller =
        AppStorageController(
            uploadUseCase,
            RecordingDeleteUseCase(),
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())

    mockMvc
        .perform(multipart("/api/v1/storage/image/svc/S3").file(multipartFile))
        .andExpect(status().isInternalServerError)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("STORAGE_CONFIGURATION_INVALID"))
        .andExpect(jsonPath("$.message").value("Internal Server Error"))
  }

  @Test
  fun `delete endpoint should map missing imageId parameter to shared 400 envelope`() {
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            RecordingDeleteUseCase(),
            AtomicAppImageProperties(),
        )
    val mockMvc = newMockMvc(controller, "/api/v1/storage/image")

    mockMvc
        .perform(delete("/api/v1/storage/image/svc/S3"))
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
        .andExpect(jsonPath("$.message").value("Required request parameter 'imageId' is missing."))
  }

  private fun newMockMvc(
      controller: AppStorageController,
      endpointPath: String,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(AtomicHttpExceptionHandler(MockEnvironment()))
        .addPlaceholderValue("atomic.app.image.endpoint-path", endpointPath)
        .build()
  }

  private fun sampleStoredImage(): StoredImage {
    return StoredImage(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        status = "ACTIVE",
        uploaderId = "member-100",
        storageType = "S3",
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
        createdAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5),
    )
  }

  private class RecordingUploadUseCase(
      var result: StoredImage,
  ) : UploadAppImageUseCase {
    var lastCommand: UploadAppImageCommand? = null
    var uploadException: RuntimeException? = null

    override fun uploadImage(command: UploadAppImageCommand): StoredImage {
      lastCommand = command
      uploadException?.let { throw it }
      return result
    }
  }

  private class RecordingDeleteUseCase : DeleteAppImageUseCase {
    var lastCommand: DeleteAppImageCommand? = null
    var deleteException: RuntimeException? = null

    override fun deleteImage(command: DeleteAppImageCommand) {
      lastCommand = command
      deleteException?.let { throw it }
    }
  }
}
