package com.infosung.atomic.app.storage.autoconfigure

import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
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
class AppImageSchemaUpgradePreflightTest {
  @Autowired private lateinit var dataSource: DataSource
  @Autowired private lateinit var jdbcTemplate: JdbcTemplate

  @Test
  fun `preflight should reject legacy varchar 255 image schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_image.sql")
    executeScript("META-INF/atomic/sql/postgresql/test/legacy_image_varchar_255.sql")

    val exception =
        assertFailsWith<IllegalStateException> {
          AppImageSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
        }

    assertTrue(exception.message!!.contains("image.file_name"))
    assertTrue(exception.message!!.contains("VARCHAR(255)"))
  }

  @Test
  fun `preflight should allow shipped text image schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_image.sql")
    executeScript("META-INF/atomic/sql/postgresql/image.sql")

    AppImageSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
  }

  @Test
  fun `preflight should allow sufficiently wide varchar image schema`() {
    executeScript("META-INF/atomic/sql/postgresql/test/drop_image.sql")
    executeScript("META-INF/atomic/sql/postgresql/test/wide_image_varchar_2048.sql")

    AppImageSchemaUpgradePreflight(jdbcTemplate).verifyOrThrow()
  }

  private fun executeScript(path: String) {
    dataSource.connection.use { ScriptUtils.executeSqlScript(it, ClassPathResource(path)) }
  }

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_image_schema_upgrade_preflight_${java.util.UUID.randomUUID()}")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
    }
  }
}
