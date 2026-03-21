package com.infosung.atomic.app.storage.adapter.`in`.web

import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
import com.infosung.atomic.app.storage.autoconfigure.AtomicAppImageProperties
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
    private val uploadAppImageUseCase: UploadAppImageUseCase,
    private val deleteAppImageUseCase: DeleteAppImageUseCase,
    private val properties: AtomicAppImageProperties,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

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
    val image =
        uploadAppImageUseCase.uploadImage(
            UploadAppImageCommand(
                serviceName = service,
                storageService = storageService,
                quality = resolvedQuality,
                uploaderId = uploaderId,
                thumbnailEnabled = resolvedThumbnailEnabled,
                uploadSource = MultipartFileUploadSource(multipartFile),
            ),
        )
    log.debug(
        "Mapping persisted image to image response DTO: imageId={}, serviceName={}, storageService={}, status={}",
        image.id,
        image.serviceName,
        image.storageService,
        image.status,
    )
    return BaseResponse.ok(ImageResponse.from(image))
  }

  @DeleteMapping("/{service}/{storageService}")
  fun deleteImage(
      @PathVariable("service") service: String,
      @PathVariable("storageService") storageService: String,
      @RequestParam("imageId") imageId: String,
      request: HttpServletRequest,
  ): BaseResponse<Nothing> {
    val uploaderId = resolveUploaderId(request)
    deleteAppImageUseCase.deleteImage(
        DeleteAppImageCommand(
            serviceName = service,
            storageService = storageService,
            imageId = imageId,
            uploaderId = uploaderId,
        ),
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
        ?: throw InvalidImageRequestException(
            "$parameterName is required when uploader parameter tracking is enabled.",
        )
  }
}
