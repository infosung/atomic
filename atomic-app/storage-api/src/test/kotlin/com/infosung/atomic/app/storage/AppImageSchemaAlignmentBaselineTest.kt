package com.infosung.atomic.app.storage

import com.infosung.atomic.app.storage.adapter.out.persistence.AppImageEntityTxService
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageEntity
import com.infosung.atomic.app.storage.adapter.out.persistence.ImageRepository
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
import org.springframework.context.annotation.Import
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
            "spring.sql.init.schema-locations=classpath:META-INF/atomic/sql/postgresql/test/drop_image.sql,classpath:META-INF/atomic/sql/postgresql/image.sql",
        ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AppImageEntityTxService::class)
@Testcontainers(disabledWithoutDocker = true)
class AppImageSchemaAlignmentBaselineTest {
  @Autowired private lateinit var imageEntityTxService: AppImageEntityTxService

  @Test
  fun `baseline runtime should validate official image sql asset against jpa mapping`() {
    assertNotNull(imageEntityTxService)
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = [ImageEntity::class])
  @EnableJpaRepositories(basePackageClasses = [ImageRepository::class])
  class TestConfiguration

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_image_schema_alignment_${UUID.randomUUID()}")

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
