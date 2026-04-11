package com.infosung.atomic.app.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
class ServiceVersionSqlAssetVendorCompatibilityTest {
  @Test
  fun `mysql asset should apply and enforce documented service version constraints`() {
    verifyServiceVersionSqlAsset(
        vendor = "mysql",
        dataSource =
            DriverManagerDataSource().apply {
              setDriverClassName(mysql.driverClassName)
              url = mysql.jdbcUrl
              username = mysql.username
              password = mysql.password
            },
    )
  }

  @Test
  fun `mariadb asset should apply and enforce documented service version constraints`() {
    verifyServiceVersionSqlAsset(
        vendor = "mariadb",
        dataSource =
            DriverManagerDataSource().apply {
              setDriverClassName(mariadb.driverClassName)
              url = mariadb.jdbcUrl
              username = mariadb.username
              password = mariadb.password
            },
    )
  }

  private fun verifyServiceVersionSqlAsset(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    val jdbcTemplate = JdbcTemplate(dataSource)
    jdbcTemplate.execute("DROP TABLE IF EXISTS service_version")
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/$vendor/service_version.sql"),
        )
        .execute(dataSource)

    jdbcTemplate.update(
        """
        INSERT INTO service_version (
          main_version,
          minor_version,
          patch_number,
          require_update,
          store_available,
          platform,
          service,
          store_url
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """
            .trimIndent(),
        2,
        0,
        1,
        true,
        true,
        "ANDROID",
        "MY_SERVICE",
        "https://store.example.com/${"x".repeat(1024)}",
    )

    val count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM service_version WHERE service = ?",
            Int::class.java,
            "MY_SERVICE",
        )
    assertEquals(1, count)

    val indexes =
        jdbcTemplate.queryForList(
            """
            SELECT index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'service_version'
            """
                .trimIndent(),
            String::class.java,
        )
    assertTrue(indexes.contains("idx_service_version_service_platform_version"))
    assertTrue(indexes.contains("idx_service_version_service_platform_required_update"))
    assertTrue(indexes.contains("uq_service_version_service_platform_semver"))

    assertThrows<DataAccessException> {
      jdbcTemplate.update(
          """
          INSERT INTO service_version (
            main_version,
            minor_version,
            patch_number,
            require_update,
            store_available,
            platform,
            service,
            store_url
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """
              .trimIndent(),
          2,
          0,
          1,
          false,
          true,
          "ANDROID",
          "MY_SERVICE",
          "https://duplicate.example.com",
      )
    }
  }

  companion object {
    @Container @JvmStatic private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")

    @Container
    @JvmStatic
    private val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4")
  }
}
