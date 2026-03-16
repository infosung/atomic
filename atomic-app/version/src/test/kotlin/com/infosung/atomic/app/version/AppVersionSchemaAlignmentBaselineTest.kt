package com.infosung.atomic.app.version

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.SpringBootVersion
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@EnabledIf("isBaselineSpringBootLine")
@DataJpaTest(
    properties =
        [
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/postgresql/test/drop_service_version.sql,classpath:META-INF/atomic/sql/postgresql/service_version.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppVersionSchemaAlignmentBaselineTest {
  @Autowired private lateinit var serviceVersionRepository: ServiceVersionRepository

  @Test
  fun `baseline runtime should validate official sql asset against jpa mapping`() {
    assertNotNull(serviceVersionRepository)
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ServiceVersionEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ServiceVersionRepository::class])
  class TestConfiguration

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_version_schema_alignment_${UUID.randomUUID()}")

    @JvmStatic
    fun isBaselineSpringBootLine(): Boolean {
      return SpringBootVersion.getVersion() == "4.0.3"
    }

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
    }
  }
}
