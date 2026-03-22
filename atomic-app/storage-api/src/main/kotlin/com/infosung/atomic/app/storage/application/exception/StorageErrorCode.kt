package com.infosung.atomic.app.storage.application.exception

enum class StorageErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  STORAGE_INVALID_IMAGE_REQUEST(400, "Storage image request is invalid."),
  STORAGE_IMAGE_NOT_FOUND(404, "Storage image was not found."),
  STORAGE_IMAGE_OWNERSHIP_MISMATCH(403, "Storage image ownership does not match."),
  STORAGE_UPLOADER_PARAMETER_REQUIRED(400, "Uploader parameter is required."),
  STORAGE_CONFIGURATION_INVALID(500, "Storage configuration is invalid."),
}
