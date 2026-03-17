package com.infosung.atomic.app.storage

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class ImageSqlAssetResourceContractTest {
  @Test
  fun `official image sql asset should exist and define documented table and index`() {
    val sql =
        ClassPathResource("META-INF/atomic/sql/postgresql/image.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS image"))
    assertTrue(sql.contains("service_name"))
    assertTrue(sql.contains("storage_service"))
    assertTrue(sql.contains("storage_type"))
    assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_image_service_storage"))
    assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_image_status_created_at"))
  }

  @Test
  fun `official image sql asset should widen externally sized fields and keep identifiers bounded`() {
    val sql =
        ClassPathResource("META-INF/atomic/sql/postgresql/image.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }

    assertTrue(sql.contains("bucket VARCHAR(255) NOT NULL"))
    assertTrue(sql.contains("service_name VARCHAR(255) NOT NULL"))
    assertTrue(sql.contains("storage_service VARCHAR(255) NOT NULL"))
    assertTrue(sql.contains("storage_type VARCHAR(255) NOT NULL"))
    assertTrue(sql.contains("file_name TEXT NULL"))
    assertTrue(sql.contains("thumbnail_file_name TEXT NULL"))
    assertTrue(sql.contains("url TEXT NOT NULL"))
    assertTrue(sql.contains("thumbnail_url TEXT NULL"))
  }
}
