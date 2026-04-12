package com.infosung.atomic.app.storage

import com.infosung.atomic.contract.database.JdbcTableIndexMetadataLoader
import com.infosung.atomic.contract.database.JdbcTableMetadataLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

class ImageSqlAssetH2CompatibilityTest {
  @Test
  fun `h2 asset should apply and support portable image schema contract`() {
    val dataSource =
        DriverManagerDataSource().apply {
          setDriverClassName("org.h2.Driver")
          url = "jdbc:h2:mem:image_sql_asset_h2;DB_CLOSE_DELAY=-1"
          username = "sa"
          password = ""
        }
    val jdbcTemplate = JdbcTemplate(dataSource)

    ResourceDatabasePopulator(
            false,
            false,
            "UTF-8",
            ClassPathResource("META-INF/atomic/sql/h2/image.sql"),
        )
        .execute(dataSource)

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
        "11111111-1111-1111-1111-111111111111",
        "bucket",
        "svc",
        "S3",
        "DELETE_PENDING",
        "user-1",
        "S3",
        "images/${"a".repeat(1200)}.png",
        "images/${"b".repeat(1100)}.webp",
        "https://cdn.example.com/${"c".repeat(1400)}",
        "https://cdn.example.com/${"d".repeat(1300)}",
        123L,
        45L,
    )

    assertEquals(
        1,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM image", Int::class.java),
    )

    val columns = JdbcTableMetadataLoader(dataSource).loadColumns("image")
    assertTrue(columns.containsKey("delete_recovery_claim_token"))
    assertTrue(columns.containsKey("delete_recovery_claimed_at"))

    val indexes = JdbcTableIndexMetadataLoader(dataSource).loadIndexes("image")
    assertTrue(indexes.containsKey("idx_image_service_storage"))
    assertTrue(indexes.containsKey("idx_image_status_created_at"))
    assertTrue(indexes.containsKey("idx_image_status_claim_created_at"))
  }
}
