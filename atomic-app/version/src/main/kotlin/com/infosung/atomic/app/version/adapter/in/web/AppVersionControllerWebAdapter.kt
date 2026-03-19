package com.infosung.atomic.app.version.adapter.`in`.web

import com.infosung.atomic.app.version.AppVersionCheckService
import com.infosung.atomic.app.version.VersionCheckRequest
import com.infosung.atomic.app.version.VersionCheckResult
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory

/** Internal web adapter for the app-version HTTP entrypoint. */
internal class AppVersionControllerWebAdapter(
    private val appVersionCheckService: AppVersionCheckService,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getVersion(
      serviceNameHeader: String?,
      platformHeader: String?,
      appVersionHeader: String?,
  ): BaseResponse<VersionCheckResult> {
    val serviceName = serviceNameHeader?.takeIf { it.isNotBlank() }
    val platform = platformHeader?.takeIf { it.isNotBlank() }
    val appVersion = appVersionHeader?.takeIf { it.isNotBlank() }

    if (serviceName == null) {
      log.warn("Version check rejected: missing header {}", ApiHeaderNames.HEADER_X_SERVICE_NAME)
      throw HttpStatusException(status = 400, message = "Service name is required.")
    }
    if (platform == null) {
      log.warn("Version check rejected: missing header {}", ApiHeaderNames.HEADER_X_PLATFORM)
      throw HttpStatusException(status = 400, message = "Platform is required.")
    }
    if (appVersion == null) {
      log.warn("Version check rejected: missing header {}", ApiHeaderNames.HEADER_X_APP_VERSION)
      throw HttpStatusException(status = 400, message = "App version is required.")
    }
    log.debug(
        "Version check request accepted: service={}, platform={}, appVersion={}",
        serviceName,
        platform,
        appVersion,
    )

    val response =
        BaseResponse.ok(
            appVersionCheckService.checkVersion(
                VersionCheckRequest(
                    service = serviceName,
                    platform = platform,
                    appVersion = appVersion,
                ),
            ),
        )
    log.debug(
        "Version check completed: service={}, platform={}, requiredUpdate={}",
        serviceName,
        platform,
        response.data?.requiredUpdate,
    )
    return response
  }
}
