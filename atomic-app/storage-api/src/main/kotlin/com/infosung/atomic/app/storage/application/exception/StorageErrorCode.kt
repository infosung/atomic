package com.infosung.atomic.app.storage.application.exception

enum class StorageErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  STORAGE_INVALID_IMAGE_REQUEST(400, "Storage image request is invalid."),
  STORAGE_IMAGE_QUALITY_INVALID(400, "Storage image quality is invalid."),
  STORAGE_FILE_NAME_REQUIRED(400, "Storage image file name is required."),
  STORAGE_IMAGE_ID_INVALID(400, "Storage imageId is invalid."),
  STORAGE_IMAGE_PATH_MISMATCH(400, "Storage image does not match request path."),
  STORAGE_STORAGE_TYPE_UNAVAILABLE(400, "Stored storage type is unavailable."),
  STORAGE_IMAGE_NOT_FOUND(404, "Storage image was not found."),
  STORAGE_IMAGE_OWNERSHIP_MISMATCH(403, "Storage image ownership does not match."),
  STORAGE_UPLOADER_PARAMETER_REQUIRED(400, "Uploader parameter is required."),
  STORAGE_CONFIGURATION_INVALID(500, "Storage configuration is invalid."),
}
