package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.header.ApiHeaderNames
import com.infosung.atomic.contract.response.BaseResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
    fun testExceptionHandler(): TestExceptionHandler = TestExceptionHandler()
  }

  @RestControllerAdvice
  class TestExceptionHandler {
    @ExceptionHandler(HttpStatusException::class)
    fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
      return ResponseEntity.status(e.status).body(BaseResponse.error(e))
    }
  }
}
