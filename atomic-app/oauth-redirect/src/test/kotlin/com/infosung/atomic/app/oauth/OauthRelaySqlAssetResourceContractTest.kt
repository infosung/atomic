package com.infosung.atomic.app.oauth

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class OauthRelaySqlAssetResourceContractTest {
  @Test
  fun `official oauth relay sql assets should exist and define documented table and index`() {
    supportedVendors().forEach { vendor ->
      val sql = loadSql(vendor)

      assertTrue(
          sql.contains("CREATE TABLE IF NOT EXISTS atomic_oauth_relay_code"),
          "missing oauth relay table DDL for $vendor",
      )
      assertTrue(sql.contains("relay_code"), "missing relay_code for $vendor")
      assertTrue(sql.contains("payload_json"), "missing payload_json for $vendor")
      assertTrue(sql.contains("expires_at"), "missing expires_at for $vendor")
      assertTrue(
          sql.contains("idx_atomic_oauth_relay_code_expires_at"),
          "missing expires_at index for $vendor",
      )
    }
  }

  private fun loadSql(vendor: String): String =
      ClassPathResource("META-INF/atomic/sql/$vendor/atomic_oauth_relay_code.sql")
          .inputStream
          .bufferedReader()
          .use { it.readText() }

  private fun supportedVendors(): List<String> = listOf("postgresql", "mysql", "mariadb")
}
