package com.infosung.atomic.app.version.application.exception

enum class AppVersionErrorCode(
    val defaultHttpStatus: Int,
    val defaultMessage: String,
) {
  VERSION_SERVICE_NAME_REQUIRED(400, "Service name is required."),
  VERSION_PLATFORM_REQUIRED(400, "Platform is required."),
  VERSION_APP_VERSION_REQUIRED(400, "App version is required."),
  VERSION_APP_VERSION_FORMAT_INVALID(400, "App version semantic format is invalid."),
  VERSION_APP_VERSION_SEGMENT_INVALID(400, "App version segment is invalid."),
  VERSION_APP_VERSION_NEGATIVE_INVALID(400, "App version must not contain negative numbers."),
  VERSION_INVALID_APP_VERSION(400, "App version is invalid."),
  VERSION_POLICY_NOT_FOUND(404, "No version policy was found."),
}
