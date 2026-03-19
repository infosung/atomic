package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.autoconfigure.AtomicAppVersionCoreAutoConfiguration
import com.infosung.atomic.app.version.autoconfigure.AtomicAppVersionWebAutoConfiguration
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionPolicy
import com.infosung.atomic.contract.header.ApiHeaderNames
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [AppVersionControllerBootSmokeContractTest.TestApplication::class],
    properties =
        [
            "atomic.app.version.enabled=true",
            "atomic.app.version.endpoint-path=/test/api/version/check",
            "atomic.app.version.default-store-url=https://store.example.com/app",
        ],
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
              "com.infosung.atomic.app.version.autoconfigure.AtomicAppVersionAutoConfiguration",
          ],
  )
  @ImportAutoConfiguration(
      AtomicAppVersionCoreAutoConfiguration::class,
      AtomicAppVersionWebAutoConfiguration::class,
  )
  class TestApplication {
    @Bean
    internal fun loadVersionPolicyPort(): LoadVersionPolicyPort {
      return object : LoadVersionPolicyPort {
        override fun loadLatestRegistered(service: String, platform: String): VersionPolicy? {
          return VersionPolicy(
              service = service,
              platform = platform,
              version = SemanticVersion(1, 2, 0),
              requireUpdate = true,
              storeAvailable = true,
              storeUrl = "https://store.example.com/app",
          )
        }

        override fun loadLatestStoreAvailable(service: String, platform: String): VersionPolicy? {
          return loadLatestRegistered(service, platform)
        }

        override fun loadRequiredUpdateTargetAbove(
            service: String,
            platform: String,
            version: SemanticVersion,
        ): VersionPolicy? {
          return if (isLowerThan(version, target = SemanticVersion(1, 2, 0))) {
            loadLatestRegistered(service, platform)
          } else {
            null
          }
        }
      }
    }
  }

  private companion object {
    fun isLowerThan(version: SemanticVersion, target: SemanticVersion): Boolean {
      return when {
        version.major != target.major -> version.major < target.major
        version.minor != target.minor -> version.minor < target.minor
        else -> version.patch < target.patch
      }
    }
  }
}
