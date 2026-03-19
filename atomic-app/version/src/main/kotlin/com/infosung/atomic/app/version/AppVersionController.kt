package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionControllerWebAdapter
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.response.BaseResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/** Common app version check API. */
@RestController
class AppVersionController(
    private val appVersionCheckService: AppVersionCheckService,
) {
  private val webAdapter = AppVersionControllerWebAdapter(appVersionCheckService)

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
  ): BaseResponse<VersionCheckResult> {
    return webAdapter.getVersion(
        serviceNameHeader = serviceNameHeader,
        platformHeader = platformHeader,
        appVersionHeader = appVersionHeader,
    )
  }
}
