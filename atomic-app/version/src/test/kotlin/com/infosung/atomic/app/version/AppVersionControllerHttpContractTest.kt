package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.domain.VersionCheckDecision
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.spring.web.exception.AtomicHttpExceptionHandler
import kotlin.test.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AppVersionControllerHttpContractTest {
  @Test
  fun `version endpoint should return documented response envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")
    val result =
        VersionCheckDecision(
            currentVersion = "1.2.4",
            userVersion = "1.2.3",
            requiredUpdate = true,
            storeUrl = "https://play.google.com/store/apps/details?id=atomic",
        )

    `when`(useCase.check("MY_SERVICE", "ANDROID", "1.2.3")).thenReturn(result)

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

    verify(useCase).check("MY_SERVICE", "ANDROID", "1.2.3")
  }

  @Test
  fun `configured endpoint path should be honored`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/internal/api/version/check")

    `when`(useCase.check("MY_SERVICE", "ANDROID", "1.2.3"))
        .thenReturn(
            VersionCheckDecision(
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
  }

  @Test
  fun `missing service header should return documented 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_SERVICE_NAME_REQUIRED"))
        .andExpect(jsonPath("$.message").value("Service name is required."))

    verifyNoInteractions(useCase)
  }

  @Test
  fun `missing platform header should return documented 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_PLATFORM_REQUIRED"))
        .andExpect(jsonPath("$.message").value("Platform is required."))

    verifyNoInteractions(useCase)
  }

  @Test
  fun `blank app version header should return documented 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
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
        .andExpect(jsonPath("$.code").value("VERSION_APP_VERSION_REQUIRED"))
        .andExpect(jsonPath("$.message").value("App version is required."))

    verifyNoInteractions(useCase)
  }

  @Test
  fun `invalid semantic version should return documented 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(
            InvalidAppVersionException(
                "Version must be semantic format: x.y.z",
                errorCode =
                    com.infosung.atomic.app.version.application.exception.AppVersionErrorCode
                        .VERSION_APP_VERSION_FORMAT_INVALID,
            ),
        )
        .`when`(useCase)
        .check("MY_SERVICE", "ANDROID", "1.2")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_APP_VERSION_FORMAT_INVALID"))
        .andExpect(jsonPath("$.message").value("Version must be semantic format: x.y.z"))
  }

  @Test
  fun `non numeric version segment should return refined 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(
            InvalidAppVersionException(
                "Version segment must be numeric: 1.a.3",
                errorCode =
                    com.infosung.atomic.app.version.application.exception.AppVersionErrorCode
                        .VERSION_APP_VERSION_SEGMENT_INVALID,
            ),
        )
        .`when`(useCase)
        .check("MY_SERVICE", "ANDROID", "1.a.3")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.a.3"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_APP_VERSION_SEGMENT_INVALID"))
        .andExpect(jsonPath("$.message").value("Version segment must be numeric: 1.a.3"))
  }

  @Test
  fun `negative version segment should return refined 400 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(
            InvalidAppVersionException(
                "Version must not contain negative numbers.",
                errorCode =
                    com.infosung.atomic.app.version.application.exception.AppVersionErrorCode
                        .VERSION_APP_VERSION_NEGATIVE_INVALID,
            ),
        )
        .`when`(useCase)
        .check("MY_SERVICE", "ANDROID", "1.-2.3")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.-2.3"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_APP_VERSION_NEGATIVE_INVALID"))
        .andExpect(jsonPath("$.message").value("Version must not contain negative numbers."))
  }

  @Test
  fun `missing version policy should return documented 404 error envelope`() {
    val useCase = mock(CheckAppVersionUseCase::class.java)
    val controller = AppVersionController(useCase)
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/version/check")

    doThrow(VersionPolicyNotFoundException("MY_SERVICE", "ANDROID"))
        .`when`(useCase)
        .check("MY_SERVICE", "ANDROID", "1.2.3")

    mockMvc
        .perform(
            get("/api/v1/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "MY_SERVICE")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.2.3"),
        )
        .andExpect(status().isNotFound)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("VERSION_POLICY_NOT_FOUND"))
        .andExpect(
            jsonPath("$.message")
                .value("No service version policy found for service=MY_SERVICE, platform=ANDROID"),
        )
  }

  private fun newMockMvc(
      controller: AppVersionController,
      endpointPath: String,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(AtomicHttpExceptionHandler(MockEnvironment()))
        .addPlaceholderValue("atomic.app.version.endpoint-path", endpointPath)
        .build()
  }
}
