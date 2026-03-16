package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AppVersionCheckServiceContainerTest {
  @Autowired private lateinit var serviceVersionRepository: ServiceVersionRepository

  @Test
  fun `checkVersion should return required update based on postgres rows`() {
    val service =
        AppVersionCheckService(serviceVersionRepository, defaultStoreUrl = "https://default.store")
    serviceVersionRepository.saveAll(
        listOf(
            policy(main = 2, minor = 0, patch = 0, requireUpdate = false),
            policy(
                main = 1,
                minor = 2,
                patch = 4,
                requireUpdate = true,
                storeUrl = "https://force.update"),
            policy(main = 1, minor = 2, patch = 3, requireUpdate = false),
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
  fun `checkVersion should return 404 when postgres has no policies`() {
    val service =
        AppVersionCheckService(serviceVersionRepository, defaultStoreUrl = "https://default.store")
    val error =
        assertFailsWith<HttpStatusException> {
          service.checkVersion(
              VersionCheckRequest(
                  service = "MY_SERVICE",
                  platform = "ANDROID",
                  appVersion = "1.2.3",
              ),
          )
        }
    assertEquals(404, error.status)
  }

  @Test
  fun `checkVersion should allow unregistered client version on postgres rows`() {
    val service =
        AppVersionCheckService(serviceVersionRepository, defaultStoreUrl = "https://default.store")
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

    val reviewerResult =
        service.checkVersion(
            VersionCheckRequest(
                service = "MY_SERVICE",
                platform = "ANDROID",
                appVersion = "2.1.0",
            ),
        )

    assertEquals("2.0.0", reviewerResult.currentVersion)
    assertEquals("2.1.0", reviewerResult.userVersion)
    assertFalse(reviewerResult.requiredUpdate)
    assertEquals("https://default.store", reviewerResult.storeUrl)
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
  class TestConfiguration

  companion object {
    @Container
    @JvmStatic
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("app_version_service_container_${UUID.randomUUID()}")

    @JvmStatic
    @DynamicPropertySource
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url", postgres::getJdbcUrl)
      registry.add("spring.datasource.username", postgres::getUsername)
      registry.add("spring.datasource.password", postgres::getPassword)
      registry.add("spring.jpa.hibernate.ddl-auto") { "create" }
    }
  }
}
