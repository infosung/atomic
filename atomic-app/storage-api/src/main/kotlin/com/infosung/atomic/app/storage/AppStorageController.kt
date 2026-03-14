package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** Common image upload/delete API. */
@RestController
@RequestMapping("\${atomic.app.image.endpoint-path:/api/v1/storage/image}")
class AppStorageController(
    private val appImageApiService: AppImageApiService,
    private val properties: AtomicAppImageProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Uploads one image and returns external image metadata.
   *
   * Path parameters:
   * - `service`: logical service name (used to resolve storage client key).
   * - `storageService`: logical storage name (for example `S3`, `R2`, `MINIO`).
   *
   * Request parameters:
   * - `quality`: optional thumbnail quality. Defaults to `atomic.app.image.default-quality`.
   * - `thumbnailEnabled`: optional thumbnail generation override. Defaults to
   *   `atomic.app.image.thumbnail-enabled`.
   * - `file`: multipart image file.
   * - optional uploader identity parameter controlled by:
   *     - `atomic.app.image.uploader-parameter-enabled`
   *     - `atomic.app.image.uploader-parameter-name`
   */
  @PostMapping(
      "/{service}/{storageService}",
      consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
  )
  fun uploadImage(
      @PathVariable("service") service: String,
      @PathVariable("storageService") storageService: String,
      @RequestParam(name = "quality", required = false) quality: Double?,
      @RequestParam(name = "thumbnailEnabled", required = false) thumbnailEnabled: Boolean?,
      @RequestParam("file") multipartFile: MultipartFile,
      request: HttpServletRequest,
  ): BaseResponse<ImageResponse> {
    val resolvedQuality = quality ?: properties.defaultQuality
    val resolvedThumbnailEnabled = thumbnailEnabled ?: properties.thumbnailEnabled
    val uploaderId = resolveUploaderId(request)
    val imageEntity =
        appImageApiService.uploadImage(
            serviceName = service,
            storageService = storageService,
            multipartFile = multipartFile,
            quality = resolvedQuality,
            uploaderId = uploaderId,
            thumbnailEnabled = resolvedThumbnailEnabled,
        )
    log.debug(
        "Mapping persisted image entity to image response DTO: imageId={}, serviceName={}, storageService={}, status={}",
        imageEntity.id,
        imageEntity.serviceName,
        imageEntity.storageService,
        imageEntity.status,
    )
    return BaseResponse.ok(
        ImageResponse.from(imageEntity),
    )
  }

  /**
   * Deletes stored image object(s) and metadata row.
   *
   * @throws com.infosung.atomic.contract.exception.HttpStatusException
   * - 400 invalid UUID, invalid path/service mismatch, unknown storage mapping
   * - 400 uploader parameter missing when tracking is enabled
   * - 404 image metadata row not found
   * - 403 uploader mismatch when tracking is enabled
   */
  @DeleteMapping("/{service}/{storageService}")
  fun deleteImage(
      @PathVariable("service") service: String,
      @PathVariable("storageService") storageService: String,
      @RequestParam("imageId") imageId: String,
      request: HttpServletRequest,
  ): BaseResponse<Nothing> {
    val uploaderId = resolveUploaderId(request)
    appImageApiService.deleteImage(
        serviceName = service,
        storageService = storageService,
        imageId = imageId,
        uploaderId = uploaderId,
    )
    return BaseResponse.ok()
  }

  private fun resolveUploaderId(request: HttpServletRequest): String? {
    if (!properties.uploaderParameterEnabled) {
      return null
    }
    val parameterName =
        properties.uploaderParameterName.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
            )
    return request.getParameter(parameterName)?.takeIf { it.isNotBlank() }
        ?: throw HttpStatusException(
            status = 400,
            message = "$parameterName is required when uploader parameter tracking is enabled.",
        )
  }
}
