package com.infosung.atomic.contract.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
class JdbcTableMetadataLoaderVendorCompatibilityTest {
  @Test
  fun `loader should resolve service version metadata on mysql`() {
    verifyServiceVersionMetadata(
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
  fun `loader should resolve service version metadata on mariadb`() {
    verifyServiceVersionMetadata(
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

  private fun verifyServiceVersionMetadata(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/$vendor/service_version.sql"),
        )
        .execute(dataSource)

    val columns = JdbcTableMetadataLoader(dataSource).loadColumns("service_version")

    assertTrue(columns.isNotEmpty())
    assertEquals(
        setOf("service", "platform", "store_url"),
        columns.keys.intersect(setOf("service", "platform", "store_url")),
    )

    val service = assertNotNull(columns["service"])
    assertTrue(service.isVariableCharacterAtLeast(255))

    val platform = assertNotNull(columns["platform"])
    assertTrue(platform.isVariableCharacterAtLeast(255))

    val storeUrl = assertNotNull(columns["store_url"])
    assertTrue(storeUrl.isTextLike())
  }

  companion object {
    @Container @JvmStatic private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")

    @Container
    @JvmStatic
    private val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4")
  }
}
