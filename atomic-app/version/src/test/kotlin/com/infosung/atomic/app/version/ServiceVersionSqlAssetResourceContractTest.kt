package com.infosung.atomic.app.version

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class ServiceVersionSqlAssetResourceContractTest {
  @Test
  fun `official service version sql assets should exist for supported relational vendors`() {
    officialVendors().forEach { vendor ->
      val sql = loadSql(vendor)

      when (vendor) {
        "oracle" -> {
          assertTrue(sql.contains("CREATE TABLE service_version"), "missing table DDL for $vendor")
          assertTrue(
              !sql.contains("CREATE TABLE IF NOT EXISTS service_version"),
              "oracle table DDL should avoid version-sensitive IF NOT EXISTS syntax",
          )
          assertTrue(
              sql.contains("CREATE INDEX idx_service_version_service_platform_required_update"),
              "missing required-update index for $vendor",
          )
          assertTrue(
              !sql.contains(
                  "CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_required_update"),
              "oracle index DDL should avoid version-sensitive IF NOT EXISTS syntax",
          )
        }

        else -> {
          assertTrue(
              sql.contains("CREATE TABLE IF NOT EXISTS service_version"),
              "missing table DDL for $vendor",
          )
          assertTrue(
              sql.contains("idx_service_version_service_platform_required_update"),
              "missing required-update index for $vendor",
          )
        }
      }
      assertTrue(sql.contains("main_version"), "missing main_version for $vendor")
      assertTrue(sql.contains("minor_version"), "missing minor_version for $vendor")
      assertTrue(sql.contains("patch_number"), "missing patch_number for $vendor")
      assertTrue(sql.contains("store_available"), "missing store_available for $vendor")
      assertTrue(
          sql.contains("uq_service_version_service_platform_semver"),
          "missing unique constraint for $vendor")
      if (vendor == "postgresql") {
        assertTrue(
            sql.contains("idx_service_version_service_platform_version"),
            "missing version index for $vendor",
        )
      }
    }
  }

  @Test
  fun `official service version sql assets should widen store url and keep identifiers bounded`() {
    officialVendors().forEach { vendor ->
      val sql = loadSql(vendor)

      when (vendor) {
        "oracle" -> {
          assertTrue(
              sql.contains("platform VARCHAR2(255 CHAR) NOT NULL"),
              "missing platform width for $vendor",
          )
          assertTrue(
              sql.contains("service VARCHAR2(255 CHAR) NOT NULL"),
              "missing service width for $vendor",
          )
          assertTrue(sql.contains("store_url CLOB NULL"), "missing widened store_url for $vendor")
        }

        else -> {
          assertTrue(
              sql.contains("platform VARCHAR(255) NOT NULL"),
              "missing platform width for $vendor",
          )
          assertTrue(
              sql.contains("service VARCHAR(255) NOT NULL"),
              "missing service width for $vendor",
          )
          assertTrue(
              sql.contains("store_url TEXT NULL"),
              "missing widened store_url for $vendor",
          )
        }
      }
    }
  }

  @Test
  fun `h2 service version sql asset should exist for test compatibility`() {
    val sql = loadSql("h2")

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS service_version"))
    assertTrue(sql.contains("store_available"))
    assertTrue(sql.contains("platform VARCHAR(255) NOT NULL"))
    assertTrue(sql.contains("service VARCHAR(255) NOT NULL"))
  }

  private fun loadSql(vendor: String): String =
      ClassPathResource("META-INF/atomic/sql/$vendor/service_version.sql")
          .inputStream
          .bufferedReader()
          .use { it.readText() }

  private fun officialVendors(): List<String> = listOf("postgresql", "mysql", "mariadb", "oracle")
}
