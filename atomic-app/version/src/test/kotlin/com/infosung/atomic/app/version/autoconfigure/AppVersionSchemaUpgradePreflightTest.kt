package com.infosung.atomic.app.version.autoconfigure

import javax.sql.DataSource
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@DataJpaTest(properties = ["spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=none"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppVersionSchemaUpgradePreflightTest {
  @Autowired private lateinit var dataSource: DataSource
  @Autowired private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `preflight should reject legacy varchar 255 service version schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_service_version.sql")
    executeScript("META-INF/atomic/sql/postgresql/test/legacy_service_version_varchar_255.sql")

    val exception =
        assertFailsWith<IllegalStateException> {
          AppVersionSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
        }

    assertTrue(exception.message!!.contains("service_version.store_url"))
    assertTrue(exception.message!!.contains("VARCHAR(255)"))
  }

  @Test
  fun `preflight should allow shipped text service version schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_service_version.sql")
    executeScript("META-INF/atomic/sql/postgresql/service_version.sql")

    AppVersionSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
  }

  @Test
  fun `preflight should allow sufficiently wide varchar service version schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_service_version.sql")
    executeScript("META-INF/atomic/sql/postgresql/test/wide_service_version_varchar_2048.sql")

    AppVersionSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
  }

  private fun executeScript(path: String) {
    dataSource.connection.use { ScriptUtils.executeSqlScript(it, ClassPathResource(path)) }
  }

  @SpringBootConfiguration @EnableAutoConfiguration class TestConfiguration

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_version_schema_upgrade_preflight_${java.util.UUID.randomUUID()}")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
    }
  }
}
