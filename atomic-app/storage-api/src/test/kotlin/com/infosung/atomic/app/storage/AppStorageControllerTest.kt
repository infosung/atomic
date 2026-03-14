package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile

class AppStorageControllerTest {
  @Test
  fun `uploadImage should use default quality when request quality is omitted`() {
    val service = mock(AppImageApiService::class.java)
    val properties = AtomicAppImageProperties().apply { defaultQuality = 0.85 }
    val controller = AppStorageController(service, properties)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val request = mock(HttpServletRequest::class.java)

    controller.uploadImage(
        service = "svc",
        storageService = "S3",
        quality = null,
        multipartFile = multipartFile,
        request = request,
    )

    verify(service)
        .uploadImage(
            serviceName = "svc",
            storageService = "S3",
            multipartFile = multipartFile,
            quality = 0.85,
            uploaderId = null,
        )
  }

  @Test
  fun `uploadImage should require uploader parameter when tracking is enabled`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn(null)

    val exception =
        assertFailsWith<HttpStatusException> {
          controller.uploadImage(
              service = "svc",
              storageService = "S3",
              quality = null,
              multipartFile = multipartFile,
              request = request,
          )
        }

    assertEquals(400, exception.status)
    verifyNoInteractions(service)
  }

  @Test
  fun `uploadImage should pass uploader parameter to service when tracking is enabled`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn("member-100")
    `when`(
            service.uploadImage(
                serviceName = "svc",
                storageService = "S3",
                multipartFile = multipartFile,
                quality = 1.0,
                uploaderId = "member-100",
            ),
        )
        .thenReturn(
            ImageEntity(
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
            ),
        )

    controller.uploadImage(
        service = "svc",
        storageService = "S3",
        quality = null,
        multipartFile = multipartFile,
        request = request,
    )

    verify(service)
        .uploadImage(
            serviceName = "svc",
            storageService = "S3",
            multipartFile = multipartFile,
            quality = 1.0,
            uploaderId = "member-100",
        )
  }

  @Test
  fun `uploadImage should ignore uploader parameter when tracking is disabled`() {
    val service = mock(AppImageApiService::class.java)
    val controller = AppStorageController(service, AtomicAppImageProperties())
    val multipartFile = MockMultipartFile("file", "profile.png", "image/png", "img".toByteArray())
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn("member-100")

    controller.uploadImage(
        service = "svc",
        storageService = "S3",
        quality = 0.9,
        multipartFile = multipartFile,
        request = request,
    )

    verify(service)
        .uploadImage(
            serviceName = "svc",
            storageService = "S3",
            multipartFile = multipartFile,
            quality = 0.9,
            uploaderId = null,
        )
  }

  @Test
  fun `deleteImage should pass uploader parameter to service when tracking is enabled`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn("member-100")

    val imageId = UUID.randomUUID().toString()
    controller.deleteImage(
        service = "svc",
        storageService = "S3",
        imageId = imageId,
        request = request,
    )

    verify(service)
        .deleteImage(
            serviceName = "svc",
            storageService = "S3",
            imageId = imageId,
            uploaderId = "member-100",
        )
  }

  @Test
  fun `deleteImage should require uploader parameter when tracking is enabled`() {
    val service = mock(AppImageApiService::class.java)
    val properties =
        AtomicAppImageProperties().apply {
          uploaderParameterEnabled = true
          uploaderParameterName = "memberId"
        }
    val controller = AppStorageController(service, properties)
    val request = mock(HttpServletRequest::class.java)
    `when`(request.getParameter("memberId")).thenReturn(null)

    val exception =
        assertFailsWith<HttpStatusException> {
          controller.deleteImage(
              service = "svc",
              storageService = "S3",
              imageId = UUID.randomUUID().toString(),
              request = request,
          )
        }

    assertEquals(400, exception.status)
    verifyNoInteractions(service)
  }
}
