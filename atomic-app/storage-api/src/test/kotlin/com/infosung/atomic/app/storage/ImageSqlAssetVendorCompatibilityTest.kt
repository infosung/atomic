package com.infosung.atomic.app.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

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

  private fun verifyImageSqlAsset(
      vendor: String,
      dataSource: DriverManagerDataSource,
  ) {
    val jdbcTemplate = JdbcTemplate(dataSource)
    jdbcTemplate.execute("DROP TABLE IF EXISTS image")
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

    val columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'image'
            """
                .trimIndent(),
            String::class.java,
        )
    assertTrue(columns.contains("delete_recovery_claim_token"))
    assertTrue(columns.contains("delete_recovery_claimed_at"))

    val indexes =
        jdbcTemplate.queryForList(
            """
            SELECT index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'image'
            """
                .trimIndent(),
            String::class.java,
        )
    assertTrue(indexes.contains("idx_image_service_storage"))
    assertTrue(indexes.contains("idx_image_status_created_at"))
    assertTrue(indexes.contains("idx_image_status_claim_created_at"))
  }

  companion object {
    @Container @JvmStatic private val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.4")

    @Container
    @JvmStatic
    private val mariadb: MariaDBContainer<*> = MariaDBContainer("mariadb:11.4")
  }
}
