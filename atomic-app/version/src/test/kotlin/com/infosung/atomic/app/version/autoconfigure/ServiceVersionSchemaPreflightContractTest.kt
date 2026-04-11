package com.infosung.atomic.app.version.autoconfigure

import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceVersionSchemaPreflightContractTest {
  @Test
  fun `verifyOrThrow should reject missing store available column`() {
    val contract = ServiceVersionSchemaPreflightContract()

    val exception =
        assertFailsWith<IllegalStateException> {
          contract.verifyOrThrow(
              mapOf(
                  "id" to VersionSchemaColumnShape("id", Types.BIGINT, "BIGINT", null),
                  "main_version" to
                      VersionSchemaColumnShape("main_version", Types.INTEGER, "INTEGER", null),
                  "minor_version" to
                      VersionSchemaColumnShape("minor_version", Types.INTEGER, "INTEGER", null),
                  "patch_number" to
                      VersionSchemaColumnShape("patch_number", Types.INTEGER, "INTEGER", null),
                  "require_update" to
                      VersionSchemaColumnShape("require_update", Types.BOOLEAN, "BOOLEAN", null),
                  "platform" to VersionSchemaColumnShape("platform", Types.VARCHAR, "VARCHAR", 255),
                  "service" to VersionSchemaColumnShape("service", Types.VARCHAR, "VARCHAR", 255),
                  "store_url" to
                      VersionSchemaColumnShape("store_url", Types.LONGVARCHAR, "TEXT", null),
                  "created_at" to
                      VersionSchemaColumnShape("created_at", Types.TIMESTAMP, "TIMESTAMP", null),
              ),
          )
        }

    assertTrue(exception.message!!.contains("service_version.store_available"))
    assertTrue(exception.message!!.contains("was not found"))
  }

  @Test
  fun `verifyOrThrow should reject narrow store url column`() {
    val contract = ServiceVersionSchemaPreflightContract()

    val exception =
        assertFailsWith<IllegalStateException> {
          contract.verifyOrThrow(
              baseColumns(
                  "store_url" to
                      VersionSchemaColumnShape("store_url", Types.VARCHAR, "VARCHAR", 255)))
        }

    assertTrue(exception.message!!.contains("service_version.store_url"))
    assertTrue(exception.message!!.contains("VARCHAR(255)"))
  }

  @Test
  fun `verifyOrThrow should allow shipped contract columns`() {
    val contract = ServiceVersionSchemaPreflightContract()

    contract.verifyOrThrow(
        baseColumns(
            "store_url" to VersionSchemaColumnShape("store_url", Types.LONGVARCHAR, "TEXT", null)))
  }

  @Test
  fun `verifyOrThrow should allow mysql longtext contract columns`() {
    val contract = ServiceVersionSchemaPreflightContract()

    contract.verifyOrThrow(
        baseColumns(
            "store_url" to
                VersionSchemaColumnShape("store_url", Types.LONGVARCHAR, "LONGTEXT", 16_777_215)))
  }

  @Test
  fun `verifyOrThrow should allow sufficiently wide mysql varchar contract columns`() {
    val contract = ServiceVersionSchemaPreflightContract()

    contract.verifyOrThrow(
        baseColumns(
            "store_url" to VersionSchemaColumnShape("store_url", Types.VARCHAR, "VARCHAR", 4096)))
  }

  private fun baseColumns(
      vararg overrides: Pair<String, VersionSchemaColumnShape>,
  ): Map<String, VersionSchemaColumnShape> {
    val defaults =
        linkedMapOf(
            "id" to VersionSchemaColumnShape("id", Types.BIGINT, "BIGINT", null),
            "main_version" to
                VersionSchemaColumnShape("main_version", Types.INTEGER, "INTEGER", null),
            "minor_version" to
                VersionSchemaColumnShape("minor_version", Types.INTEGER, "INTEGER", null),
            "patch_number" to
                VersionSchemaColumnShape("patch_number", Types.INTEGER, "INTEGER", null),
            "require_update" to
                VersionSchemaColumnShape("require_update", Types.BOOLEAN, "BOOLEAN", null),
            "store_available" to
                VersionSchemaColumnShape("store_available", Types.BOOLEAN, "BOOLEAN", null),
            "platform" to VersionSchemaColumnShape("platform", Types.VARCHAR, "VARCHAR", 255),
            "service" to VersionSchemaColumnShape("service", Types.VARCHAR, "VARCHAR", 255),
            "store_url" to VersionSchemaColumnShape("store_url", Types.LONGVARCHAR, "TEXT", null),
            "created_at" to
                VersionSchemaColumnShape("created_at", Types.TIMESTAMP, "TIMESTAMP", null),
        )
    overrides.forEach { (name, column) -> defaults[name] = column }
    return defaults
  }
}
