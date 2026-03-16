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
  }
}
