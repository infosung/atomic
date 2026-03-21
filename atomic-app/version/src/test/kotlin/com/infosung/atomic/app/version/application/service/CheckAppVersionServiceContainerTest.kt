package com.infosung.atomic.app.version.application.service

import com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
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
class CheckAppVersionServiceContainerTest {
  @Autowired private lateinit var serviceVersionRepository: ServiceVersionRepository

  @Test
  fun `check should return required update based on postgres rows`() {
    val service = newService()
    serviceVersionRepository.saveAll(
        listOf(
            policy(main = 2, minor = 0, patch = 0, requireUpdate = false),
            policy(
                main = 1,
                minor = 2,
                patch = 4,
                requireUpdate = true,
                storeUrl = "https://force.update",
            ),
            policy(main = 1, minor = 2, patch = 3, requireUpdate = false),
        ),
    )

    val result = service.check(service = "MY_SERVICE", platform = "ANDROID", appVersion = "1.2.3")

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
  }

  @Test
  fun `check should return 404-equivalent application error when postgres has no policies`() {
    val service = newService()

    val error =
        assertFailsWith<VersionPolicyNotFoundException> {
          service.check(service = "MY_SERVICE", platform = "ANDROID", appVersion = "1.2.3")
        }

    assertEquals(
        "No service version policy found for service=MY_SERVICE, platform=ANDROID",
        error.message,
    )
  }

  @Test
  fun `check should allow unregistered client version on postgres rows`() {
    val service = newService()
    serviceVersionRepository.saveAll(
        listOf(
            policy(
                main = 2,
                minor = 1,
                patch = 0,
                requireUpdate = true,
                storeUrl = "https://not-ready.update",
                storeAvailable = false,
            ),
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

    val result = service.check(service = "MY_SERVICE", platform = "ANDROID", appVersion = "1.2.3")

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)

    val reviewerResult =
        service.check(service = "MY_SERVICE", platform = "ANDROID", appVersion = "2.1.0")

    assertEquals("2.0.0", reviewerResult.currentVersion)
    assertEquals("2.1.0", reviewerResult.userVersion)
    assertFalse(reviewerResult.requiredUpdate)
    assertEquals("https://default.store", reviewerResult.storeUrl)
  }

  @Test
  fun `check should choose highest required-update target when multiple higher rows exist`() {
    val service = newService()
    serviceVersionRepository.saveAll(
        listOf(
            policy(main = 2, minor = 0, patch = 0, requireUpdate = false, storeAvailable = true),
            policy(
                main = 1,
                minor = 3,
                patch = 0,
                requireUpdate = true,
                storeUrl = "https://force.update/highest",
                storeAvailable = true,
            ),
            policy(
                main = 1,
                minor = 2,
                patch = 4,
                requireUpdate = true,
                storeUrl = "https://force.update/lower",
                storeAvailable = true,
            ),
            policy(main = 1, minor = 2, patch = 3, requireUpdate = false, storeAvailable = true),
        ),
    )

    val result = service.check(service = "MY_SERVICE", platform = "ANDROID", appVersion = "1.2.3")

    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update/highest", result.storeUrl)
  }

  private fun newService(): CheckAppVersionService {
    return CheckAppVersionService(
        loadVersionPolicyPort = JpaLoadVersionPolicyAdapter(serviceVersionRepository),
        defaultStoreUrl = "https://default.store",
    )
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
            .withDatabaseName("check_app_version_service_${UUID.randomUUID()}")

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
