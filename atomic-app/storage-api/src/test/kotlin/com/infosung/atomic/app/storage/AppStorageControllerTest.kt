package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.`in`.web.AppStorageController
import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.app.storage.domain.StoredImage
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile

class AppStorageControllerTest {
  @Test
  fun `uploadImage should use default quality when request quality is omitted`() {
    val uploadUseCase = RecordingUploadUseCase(sampleStoredImage())
    val controller =
        AppStorageController(
            uploadUseCase,
            RecordingDeleteUseCase(),
            AtomicAppImageProperties().apply { defaultQuality = 0.85 },
        )

    controller.uploadImage(
        service = "svc",
        storageService = "S3",
        quality = null,
        thumbnailEnabled = null,
        multipartFile = sampleMultipartFile(),
        request = mock(HttpServletRequest::class.java),
    )

    assertEquals(0.85, uploadUseCase.lastCommand!!.quality)
    assertEquals(true, uploadUseCase.lastCommand!!.thumbnailEnabled)
    assertEquals("svc", uploadUseCase.lastCommand!!.serviceName)
    assertEquals("S3", uploadUseCase.lastCommand!!.storageService)
  }

  @Test
  fun `uploadImage should require uploader parameter when tracking is enabled`() {
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
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn(null)

    val exception =
        assertFailsWith<InvalidImageRequestException> {
          controller.uploadImage(
              service = "svc",
              storageService = "S3",
              quality = null,
              thumbnailEnabled = null,
              multipartFile = sampleMultipartFile(),
              request = request,
          )
        }

    assertEquals(
        "memberId is required when uploader parameter tracking is enabled.",
        exception.message,
    )
  }

  @Test
  fun `uploadImage should pass uploader parameter to use case when tracking is enabled`() {
    val properties =
        AtomicAppImageProperties().apply {
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
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn("member-100")

    controller.uploadImage(
        service = "svc",
        storageService = "S3",
        quality = null,
        thumbnailEnabled = null,
        multipartFile = sampleMultipartFile(),
        request = request,
    )

    assertEquals("member-100", uploadUseCase.lastCommand!!.uploaderId)
  }

  @Test
  fun `deleteImage should pass uploader parameter and imageId to use case`() {
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val deleteUseCase = RecordingDeleteUseCase()
    val controller =
        AppStorageController(
            RecordingUploadUseCase(sampleStoredImage()),
            deleteUseCase,
            properties,
        )
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn("member-100")
    val imageId = UUID.randomUUID().toString()

    controller.deleteImage(
        service = "svc",
        storageService = "S3",
        imageId = imageId,
        request = request,
    )

    assertEquals(imageId, deleteUseCase.lastCommand!!.imageId)
    assertEquals("member-100", deleteUseCase.lastCommand!!.uploaderId)
  }

  private fun sampleMultipartFile(): MockMultipartFile {
    return MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
  }

  private fun sampleStoredImage(): StoredImage {
    return StoredImage(
        id = UUID.randomUUID(),
        bucket = "bucket",
        serviceName = "svc",
        storageService = "S3",
        uploaderId = "member-100",
        storageType = "S3",
        fileName = "images/test/original.png",
        thumbnailFileName = "images/test/original_thumb.webp",
        url = "https://cdn/images/test/original.png",
        thumbnailUrl = "https://cdn/images/test/original_thumb.webp",
        fileSize = 10,
    )
  }

  private class RecordingUploadUseCase(
      private val result: StoredImage,
  ) : UploadAppImageUseCase {
    var lastCommand: UploadAppImageCommand? = null

    override fun uploadImage(command: UploadAppImageCommand): StoredImage {
      lastCommand = command
      return result
    }
  }

  private class RecordingDeleteUseCase : DeleteAppImageUseCase {
    var lastCommand: DeleteAppImageCommand? = null

    override fun deleteImage(command: DeleteAppImageCommand) {
      lastCommand = command
    }
  }
}
