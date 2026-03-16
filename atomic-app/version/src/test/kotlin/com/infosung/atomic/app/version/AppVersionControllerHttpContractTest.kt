package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.response.BaseResponse
import kotlin.test.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class AppVersionControllerHttpContractTest {
  @Test
  fun `version endpoint should return documented response envelope`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")
    val result =
        VersionCheckResult(
            currentVersion = "1.2.4",
            userVersion = "1.2.3",
            requiredUpdate = true,
            storeUrl = "https://play.google.com/store/apps/details?id=atomic",
        )

    `when`(
            service.checkVersion(
                VersionCheckRequest(
                    service = "MY_SERVICE",
                    platform = "ANDROID",
                    appVersion = "1.2.3",
                ),
            ),
        )
        .thenReturn(result)

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isOk)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))
        .andExpect(jsonPath("$.data.currentVersion").value("1.2.4"))
        .andExpect(jsonPath("$.data.userVersion").value("1.2.3"))
        .andExpect(jsonPath("$.data.requiredUpdate").value(true))
        .andExpect(
            jsonPath("$.data.storeUrl")
                .value("https://play.google.com/store/apps/details?id=atomic"),
        )

    verify(service)
        .checkVersion(
            VersionCheckRequest(
                service = "MY_SERVICE",
                platform = "ANDROID",
                appVersion = "1.2.3",
            ),
        )
  }

  @Test
  fun `configured endpoint path should be honored`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/internal/api/version/check")

    `when`(
            service.checkVersion(
                VersionCheckRequest(
                    service = "MY_SERVICE",
                    platform = "ANDROID",
                    appVersion = "1.2.3",
                ),
            ),
        )
        .thenReturn(
            VersionCheckResult(
                currentVersion = "1.2.3",
                userVersion = "1.2.3",
                requiredUpdate = false,
                storeUrl = "https://www.infosung.com",
            ),
        )

    mockMvc
        .perform(
            get("/internal/api/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.requiredUpdate").value(false))

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isNotFound)
  }

  @Test
  fun `missing service header should return documented 400 error envelope`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("Service name is required."))

    verifyNoInteractions(service)
  }

  @Test
  fun `blank app version header should return documented 400 error envelope`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "   "),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("App version is required."))

    verifyNoInteractions(service)
  }

  @Test
  fun `invalid semantic version should return documented 400 error envelope`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(HttpStatusException(status = 400, message = "Version must be semantic format: x.y.z"))
        .`when`(service)
        .checkVersion(
            VersionCheckRequest(
                service = "MY_SERVICE",
                platform = "ANDROID",
                appVersion = "1.2",
            ),
        )

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("Version must be semantic format: x.y.z"))
  }

  @Test
  fun `missing version policy should return documented 404 error envelope`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(HttpStatusException(status = 404, message = "No version policy found."))
        .`when`(service)
        .checkVersion(
            VersionCheckRequest(
                service = "MY_SERVICE",
                platform = "ANDROID",
                appVersion = "1.2.3",
            ),
        )

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isNotFound)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("No version policy found."))
  }

  private fun newMockMvc(
      controller: AppVersionController,
      endpointPath: String,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(TestHttpStatusExceptionHandler())
        .addPlaceholderValue("atomic.app.version.endpoint-path", endpointPath)
        .build()
  }

  @RestControllerAdvice
  private class TestHttpStatusExceptionHandler {
    @ExceptionHandler(HttpStatusException::class)
    fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
      return ResponseEntity.status(e.status).body(BaseResponse.error(e))
    }
  }
}
