package com.infosung.atomic.app.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/postgresql/service_version.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppVersionMigrationAssetContractTest.TestConfiguration::class)
@Testcontainers(disabledWithoutDocker = true)
class AppVersionMigrationAssetContractTest {
  @Autowired private lateinit var serviceVersionRepository: ServiceVersionRepository
  @Autowired private lateinit var jdbcTemplate: JdbcTemplate
  @Autowired private lateinit var service: AppVersionCheckService

  @Test
  fun `official service version sql asset should support rollout safe version checks`() {
    serviceVersionRepository.saveAll(
        listOf(
            policy(
                main = 2,
                minor = 1,
                patch = 0,
                requireUpdate = true,
                storeUrl = "https://not-ready.update",
                storeAvailable = false),
            policy(main = 2, minor = 0, patch = 0, requireUpdate = false, storeAvailable = true),
            policy(
                main = 1,
                minor = 2,
                patch = 4,
                requireUpdate = true,
                storeUrl = "https://force.update",
                storeAvailable = true),
        ),
    )

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "MY_SERVICE",
                platform = "ANDROID",
                appVersion = "1.2.3",
            ),
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
  }

  @Test
  fun `official service version sql asset should create documented index`() {
    val indexes =
        jdbcTemplate.queryForList(
            """
            SELECT indexname
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND tablename = 'service_version'
            """
                .trimIndent(),
            String::class.java,
        )

    assertTrue(indexes.contains("idx_service_version_service_platform_version"))
    assertTrue(indexes.contains("idx_service_version_service_platform_required_update"))
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
    fun appVersionCheckService(
        serviceVersionRepository: ServiceVersionRepository
    ): AppVersionCheckService {
      return AppVersionCheckService(
          serviceVersionRepository = serviceVersionRepository,
          defaultStoreUrl = "https://default.store",
      )
    }
  }

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
    }
  }
}
