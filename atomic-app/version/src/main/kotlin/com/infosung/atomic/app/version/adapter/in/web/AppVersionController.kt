package com.infosung.atomic.app.version.adapter.`in`.web

import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/** Web adapter for the app version check API. */
@RestController
class AppVersionController(
    private val checkAppVersionUseCase: CheckAppVersionUseCase,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  /**
   * Returns whether client app update is required.
   *
   * Required headers:
   * - `X-Service-Name`: service identifier used by version policy table.
   * - `X-Platform`: platform identifier (for example `ANDROID`, `IOS`, `WEB`).
   * - `X-App-Version`: semantic version (`x.y.z`) of client app.
   *
   * @throws HttpStatusException 400 when any required header is missing/blank.
   * @throws HttpStatusException 400 when app version format is invalid.
   * @throws HttpStatusException 404 when no version policy rows exist for service/platform.
   */
  @GetMapping("\${atomic.app.version.endpoint-path:/api/v1/version/check}")
  fun getVersion(
      @RequestHeader(ApiHeaderNames.HEADER_X_SERVICE_NAME, required = false)
      serviceNameHeader: String?,
      @RequestHeader(ApiHeaderNames.HEADER_X_PLATFORM, required = false) platformHeader: String?,
      @RequestHeader(ApiHeaderNames.HEADER_X_APP_VERSION, required = false)
      appVersionHeader: String?,
  ): BaseResponse<AppVersionCheckResponseDto> {
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
        "Version check request accepted at web adapter: service={}, platform={}, appVersion={}",
        serviceName,
        platform,
        appVersion,
    )

    val decision =
        checkAppVersionUseCase.check(
            service = serviceName,
            platform = platform,
            appVersion = appVersion,
        )
    val response = BaseResponse.ok(AppVersionCheckResponseMapper.toResponse(decision))
    log.debug(
        "Version check completed at web adapter: service={}, platform={}, requiredUpdate={}",
        serviceName,
        platform,
        response.data?.requiredUpdate,
    )
    return response
  }
}
