package com.infosung.atomic.app.version

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class ServiceVersionSqlAssetResourceContractTest {
  @Test
  fun `official service version sql asset should exist and define documented table and index`() {
    val sql =
        ClassPathResource("META-INF/atomic/sql/postgresql/service_version.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS service_version"))
    assertTrue(sql.contains("main_version"))
    assertTrue(sql.contains("minor_version"))
    assertTrue(sql.contains("patch_number"))
    assertTrue(sql.contains("store_available"))
    assertTrue(
        sql.contains("CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_version"))
    assertTrue(
        sql.contains(
            "CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_required_update"),
    )
    assertTrue(
        sql.contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_service_version_service_platform_semver"),
    )
  }
}
