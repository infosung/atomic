package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.autoconfigure.AppVersionSchemaUpgradePreflight
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/h2/service_version.sql",
        ],
)
@Import(AppVersionH2CompatibilityContractTest.TestConfiguration::class)
class AppVersionH2CompatibilityContractTest {
  @Autowired private lateinit var serviceVersionRepository: ServiceVersionRepository
  @Autowired private lateinit var checkAppVersionUseCase: CheckAppVersionUseCase
  @Autowired private lateinit var appVersionSchemaUpgradePreflight: AppVersionSchemaUpgradePreflight

  @Test
  fun `h2 asset should support preflight and version check use case`() {
    appVersionSchemaUpgradePreflight.verifyOrThrow()

    serviceVersionRepository.saveAll(
        listOf(
            policy(main = 2, minor = 0, patch = 0, requireUpdate = false, storeAvailable = true),
            policy(
                main = 1,
                minor = 2,
                patch = 4,
                requireUpdate = true,
                storeUrl = "https://force.update",
                storeAvailable = true,
            ),
        ),
    )

    val result =
        checkAppVersionUseCase.check(
            service = "MY_SERVICE",
            platform = "ANDROID",
            appVersion = "1.2.3",
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertEquals(true, result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
  }

  private fun policy(
      main: Int,
      minor: Int,
      patch: Int,
      requireUpdate: Boolean,
      storeUrl: String? = null,
      storeAvailable: Boolean = true,
  ): ServiceVersionEntity {
    return ServiceVersionEntity(
            mainVersion = main,
            minorVersion = minor,
            patchNumber = patch,
            requireUpdate = requireUpdate,
            service = "MY_SERVICE",
            platform = "ANDROID",
            storeUrl = storeUrl,
        )
        .also { it.storeAvailable = storeAvailable }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ServiceVersionEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ServiceVersionRepository::class])
  class TestConfiguration {
    @Bean
    fun checkAppVersionUseCase(
        serviceVersionRepository: ServiceVersionRepository,
    ): CheckAppVersionUseCase {
      return com.infosung.atomic.app.version.application.service.CheckAppVersionService(
          loadVersionPolicyPort =
              com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter(
                  serviceVersionRepository,
              ),
          defaultStoreUrl = "https://default.store",
      )
    }

    @Bean
    fun appVersionSchemaUpgradePreflight(
        jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate,
    ): AppVersionSchemaUpgradePreflight {
      return AppVersionSchemaUpgradePreflight(jdbcTemplate)
    }
  }
}
