package com.infosung.atomic.app.storage.adapter.`in`.web

import com.infosung.atomic.app.storage.application.exception.StorageApplicationException
import com.infosung.atomic.app.storage.application.exception.StorageConfigurationException
import com.infosung.atomic.app.storage.application.exception.StorageErrorCode
import com.infosung.atomic.app.storage.application.model.DeleteAppImageCommand
import com.infosung.atomic.app.storage.application.model.UploadAppImageCommand
import com.infosung.atomic.app.storage.application.port.`in`.DeleteAppImageUseCase
import com.infosung.atomic.app.storage.application.port.`in`.UploadAppImageUseCase
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
    val image =
        try {
          val uploaderId = resolveUploaderId(request)
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
        } catch (e: StorageApplicationException) {
          throw storageHttpStatusException(
              operation = "upload image",
              service = service,
              storageService = storageService,
              exception = e,
          )
        }
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
    try {
      val uploaderId = resolveUploaderId(request)
      deleteAppImageUseCase.deleteImage(
          DeleteAppImageCommand(
              serviceName = service,
              storageService = storageService,
              imageId = imageId,
              uploaderId = uploaderId,
          ),
      )
    } catch (e: StorageApplicationException) {
      throw storageHttpStatusException(
          operation = "delete image",
          service = service,
          storageService = storageService,
          exception = e,
      )
    }
    return BaseResponse.ok()
  }

  private fun resolveUploaderId(request: HttpServletRequest): String? {
    if (!properties.uploaderParameterEnabled) {
      return null
    }
    val parameterName =
        properties.uploaderParameterName.trim().takeIf { it.isNotBlank() }
            ?: throw StorageConfigurationException(
                "atomic.app.image.uploader-parameter-name must not be blank when uploader parameter tracking is enabled.",
            )
    return request.getParameter(parameterName)?.takeIf { it.isNotBlank() }
        ?: throw StorageApplicationException(
            errorCode = StorageErrorCode.STORAGE_UPLOADER_PARAMETER_REQUIRED,
            "$parameterName is required when uploader parameter tracking is enabled.",
        )
  }

  private fun storageHttpStatusException(
      operation: String,
      service: String,
      storageService: String,
      exception: StorageApplicationException,
  ): HttpStatusException {
    val status = exception.errorCode.defaultHttpStatus
    if (status >= 500) {
      log.error(
          "Storage request failed at web adapter: operation={}, service={}, storageService={}, errorCode={}, message={}",
          operation,
          service,
          storageService,
          exception.errorCode,
          exception.message,
          exception,
      )
    } else {
      log.warn(
          "Storage request rejected at web adapter: operation={}, service={}, storageService={}, errorCode={}, message={}",
          operation,
          service,
          storageService,
          exception.errorCode,
          exception.message,
      )
    }
    return HttpStatusException(
        status = status,
        code = exception.errorCode.name,
        message = exception.message ?: exception.errorCode.defaultMessage,
        cause = exception,
    )
  }
}
