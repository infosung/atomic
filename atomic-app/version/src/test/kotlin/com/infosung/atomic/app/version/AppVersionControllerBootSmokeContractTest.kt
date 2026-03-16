package com.infosung.atomic.app.version

import com.infosung.atomic.contract.header.ApiHeaderNames
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [AppVersionControllerBootSmokeContractTest.TestApplication::class],
    properties = ["atomic.app.version.endpoint-path=/test/api/version/check"],
)
@AutoConfigureMockMvc
class AppVersionControllerBootSmokeContractTest {
  @jakarta.annotation.Resource private lateinit var mockMvc: MockMvc

  @Test
  fun `boot mvc should expose version check endpoint with documented headers`() {
    mockMvc
        .perform(
            get("/test/api/version/check")
                .header(ApiHeaderNames.HEADER_X_SERVICE_NAME, "svc")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.0.0"),
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data.currentVersion").value("1.2.0"))
        .andExpect(jsonPath("$.data.requiredUpdate").value(true))
  }

  @Test
  fun `boot mvc should keep documented 400 status without custom exception advice`() {
    mockMvc
        .perform(
            get("/test/api/version/check")
                .header(ApiHeaderNames.HEADER_X_PLATFORM, "ANDROID")
                .header(ApiHeaderNames.HEADER_X_APP_VERSION, "1.0.0"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("HttpStatusException"))
        .andExpect(jsonPath("$.message").value("Service name is required."))
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      excludeName =
          [
              "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
              "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
              "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
          ],
  )
  class TestApplication {
    @Bean
    fun appVersionCheckService(): AppVersionCheckService =
        mock(AppVersionCheckService::class.java) { invocation ->
          when (invocation.method.name) {
            "checkVersion" ->
                VersionCheckResult(
                    currentVersion = "1.2.0",
                    userVersion = "1.0.0",
                    requiredUpdate = true,
                    storeUrl = "https://store.example.com/app",
                )
            else -> null
          }
        }

    @Bean
    fun appVersionController(
        appVersionCheckService: AppVersionCheckService,
    ): AppVersionController {
      return AppVersionController(appVersionCheckService = appVersionCheckService)
    }

    @Bean
    fun appVersionHttpExceptionHandler(): AppVersionHttpExceptionHandler {
      return AppVersionHttpExceptionHandler()
    }
  }
}
