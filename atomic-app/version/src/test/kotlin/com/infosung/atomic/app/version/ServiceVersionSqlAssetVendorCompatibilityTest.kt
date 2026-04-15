package com.infosung.atomic.app.version

import com.infosung.atomic.contract.database.JdbcTableIndexMetadataLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer

@EnabledIfEnvironmentVariable(named = "ATOMIC_RUN_ORACLE_COMPATIBILITY", matches = "true")
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

  @Test
  fun `oracle asset should apply and enforce documented service version constraints`() {
    verifyServiceVersionSqlAsset(
        vendor = "oracle",
        dataSource =
            DriverManagerDataSource().apply {
              setDriverClassName(oracle.driverClassName)
              url = oracle.jdbcUrl
              username = oracle.username
              password = oracle.password
            },
    )
  }

  private fun verifyServiceVersionSqlAsset(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    val jdbcTemplate = JdbcTemplate(dataSource)
    dropTableQuietly(jdbcTemplate, "service_version")
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

    val indexes = JdbcTableIndexMetadataLoader(dataSource).loadIndexes("service_version")
    assertTrue(indexes.containsKey("idx_service_version_service_platform_required_update"))
    assertTrue(indexes.containsKey("uq_service_version_service_platform_semver"))
    assertTrue(!indexes.containsKey("idx_service_version_service_platform_version"))

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

  private fun dropTableQuietly(
      jdbcTemplate: JdbcTemplate,
      tableName: String,
  ) {
    runCatching { jdbcTemplate.execute("DROP TABLE $tableName") }
  }

  companion object {
    @Container @JvmStatic private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")

    @Container
    @JvmStatic
    private val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4")

    @Container
    @JvmStatic
    private val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")
  }
}
