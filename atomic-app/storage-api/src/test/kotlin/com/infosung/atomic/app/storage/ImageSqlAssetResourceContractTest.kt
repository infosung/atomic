package com.infosung.atomic.app.storage

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class ImageSqlAssetResourceContractTest {
  @Test
  fun `official image sql assets should exist and define documented table and indexes`() {
    officialVendors().forEach { vendor ->
      val sql = loadSql(vendor)

      assertTrue(
          sql.contains("CREATE TABLE IF NOT EXISTS image"), "missing image table DDL for $vendor")
      assertTrue(sql.contains("service_name"), "missing service_name for $vendor")
      assertTrue(sql.contains("storage_service"), "missing storage_service for $vendor")
      assertTrue(sql.contains("storage_type"), "missing storage_type for $vendor")
      assertTrue(
          sql.contains("idx_image_service_storage"), "missing service/storage index for $vendor")
      assertTrue(
          sql.contains("idx_image_status_created_at"), "missing status/created index for $vendor")
    }
  }

  @Test
  fun `official image sql assets should widen externally sized fields and keep identifiers bounded`() {
    officialVendors().forEach { vendor ->
      val sql = loadSql(vendor)

      when (vendor) {
        "oracle" -> {
          assertTrue(
              sql.contains("bucket VARCHAR2(255 CHAR) NOT NULL"),
              "missing bucket width for $vendor",
          )
          assertTrue(
              sql.contains("service_name VARCHAR2(255 CHAR) NOT NULL"),
              "missing service_name width for $vendor",
          )
          assertTrue(
              sql.contains("storage_service VARCHAR2(255 CHAR) NOT NULL"),
              "missing storage_service width for $vendor",
          )
          assertTrue(
              sql.contains("storage_type VARCHAR2(255 CHAR) NOT NULL"),
              "missing storage_type width for $vendor",
          )
          assertTrue(sql.contains("file_name CLOB NULL"), "missing file_name CLOB for $vendor")
          assertTrue(
              sql.contains("thumbnail_file_name CLOB NULL"),
              "missing thumbnail_file_name CLOB for $vendor",
          )
          assertTrue(sql.contains("url CLOB NOT NULL"), "missing url CLOB for $vendor")
          assertTrue(
              sql.contains("thumbnail_url CLOB NULL"),
              "missing thumbnail_url CLOB for $vendor",
          )
        }

        else -> {
          assertTrue(
              sql.contains("bucket VARCHAR(255) NOT NULL"),
              "missing bucket width for $vendor",
          )
          assertTrue(
              sql.contains("service_name VARCHAR(255) NOT NULL"),
              "missing service_name width for $vendor",
          )
          assertTrue(
              sql.contains("storage_service VARCHAR(255) NOT NULL"),
              "missing storage_service width for $vendor",
          )
          assertTrue(
              sql.contains("storage_type VARCHAR(255) NOT NULL"),
              "missing storage_type width for $vendor",
          )
          assertTrue(sql.contains("file_name TEXT NULL"), "missing file_name TEXT for $vendor")
          assertTrue(
              sql.contains("thumbnail_file_name TEXT NULL"),
              "missing thumbnail_file_name TEXT for $vendor",
          )
          assertTrue(sql.contains("url TEXT NOT NULL"), "missing url TEXT for $vendor")
          assertTrue(
              sql.contains("thumbnail_url TEXT NULL"),
              "missing thumbnail_url TEXT for $vendor",
          )
        }
      }
    }
  }

  @Test
  fun `h2 image sql asset should exist for test compatibility`() {
    val sql = loadSql("h2")

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS image"))
    assertTrue(sql.contains("delete_recovery_claim_token"))
    assertTrue(sql.contains("delete_recovery_claimed_at"))
  }

  private fun loadSql(vendor: String): String =
      ClassPathResource("META-INF/atomic/sql/$vendor/image.sql").inputStream.bufferedReader().use {
        it.readText()
      }

  private fun officialVendors(): List<String> = listOf("postgresql", "mysql", "mariadb", "oracle")
}
