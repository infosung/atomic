package com.infosung.atomic.app.oauth

import kotlin.test.Test
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource

class OauthRelaySqlAssetResourceContractTest {
  @Test
  fun `official oauth relay sql asset should exist and define documented table and index`() {
    val sql =
        ClassPathResource("META-INF/atomic/sql/postgresql/atomic_oauth_relay_code.sql")
            .inputStream
            .bufferedReader()
            .use { it.readText() }

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS atomic_oauth_relay_code"))
    assertTrue(sql.contains("relay_code"))
    assertTrue(sql.contains("payload_json"))
    assertTrue(sql.contains("expires_at"))
    assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_atomic_oauth_relay_code_expires_at"))
  }
}
