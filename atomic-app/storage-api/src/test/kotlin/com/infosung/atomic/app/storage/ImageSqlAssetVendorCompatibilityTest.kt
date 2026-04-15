package com.infosung.atomic.app.storage

import com.infosung.atomic.contract.database.JdbcTableIndexMetadataLoader
import com.infosung.atomic.contract.database.JdbcTableMetadataLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.core.io.ClassPathResource
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
class ImageSqlAssetVendorCompatibilityTest {
  @Test
  fun `mysql asset should apply and support long image fields`() {
    verifyImageSqlAsset(
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
  fun `mariadb asset should apply and support long image fields`() {
    verifyImageSqlAsset(
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
  fun `oracle asset should apply and support long image fields`() {
    verifyImageSqlAsset(
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

  private fun verifyImageSqlAsset(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    val jdbcTemplate = JdbcTemplate(dataSource)
    dropTableQuietly(jdbcTemplate, "image")
    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/$vendor/image.sql"),
        )
        .execute(dataSource)

    val imageId = "11111111-1111-1111-1111-111111111111"
    val fileName = "images/${"a".repeat(1200)}.png"
    val thumbnailFileName = "images/${"b".repeat(1100)}.webp"
    val url = "https://cdn.example.com/${"c".repeat(1400)}"
    val thumbnailUrl = "https://cdn.example.com/${"d".repeat(1300)}"

    jdbcTemplate.update(
        """
        INSERT INTO image (
          id,
          bucket,
          service_name,
          storage_service,
          status,
          uploader_id,
          storage_type,
          file_name,
          thumbnail_file_name,
          url,
          thumbnail_url,
          file_size,
          thumbnail_file_size
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
            .trimIndent(),
        imageId,
        "bucket",
        "svc",
        "S3",
        "DELETE_PENDING",
        "user-1",
        "S3",
        fileName,
        thumbnailFileName,
        url,
        thumbnailUrl,
        123L,
        45L,
    )

    assertEquals(
        fileName,
        jdbcTemplate.queryForObject(
            "SELECT file_name FROM image WHERE id = ?",
            String::class.java,
            imageId,
        ),
    )
    assertEquals(
        url,
        jdbcTemplate.queryForObject(
            "SELECT url FROM image WHERE id = ?",
            String::class.java,
            imageId,
        ),
    )

    val columns = JdbcTableMetadataLoader(dataSource).loadColumns("image")
    assertTrue(columns.containsKey("delete_recovery_claim_token"))
    assertTrue(columns.containsKey("delete_recovery_claimed_at"))

    val indexes = JdbcTableIndexMetadataLoader(dataSource).loadIndexes("image")
    assertTrue(indexes.containsKey("idx_image_service_storage"))
    assertTrue(indexes.containsKey("idx_image_status_created_at"))
    assertTrue(indexes.containsKey("idx_image_status_claim_created_at"))
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
