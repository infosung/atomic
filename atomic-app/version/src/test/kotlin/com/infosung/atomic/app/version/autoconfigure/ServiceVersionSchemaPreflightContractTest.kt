package com.infosung.atomic.app.version.autoconfigure

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
                  "id" to VersionSchemaColumnShape("id", "bigint", null),
                  "main_version" to VersionSchemaColumnShape("main_version", "integer", null),
                  "minor_version" to VersionSchemaColumnShape("minor_version", "integer", null),
                  "patch_number" to VersionSchemaColumnShape("patch_number", "integer", null),
                  "require_update" to VersionSchemaColumnShape("require_update", "boolean", null),
                  "platform" to VersionSchemaColumnShape("platform", "character varying", 255),
                  "service" to VersionSchemaColumnShape("service", "character varying", 255),
                  "store_url" to VersionSchemaColumnShape("store_url", "text", null),
                  "created_at" to
                      VersionSchemaColumnShape("created_at", "timestamp without time zone", null),
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
                  "store_url" to VersionSchemaColumnShape("store_url", "character varying", 255)))
        }

    assertTrue(exception.message!!.contains("service_version.store_url"))
    assertTrue(exception.message!!.contains("VARCHAR(255)"))
  }

  @Test
  fun `verifyOrThrow should allow shipped contract columns`() {
    val contract = ServiceVersionSchemaPreflightContract()

    contract.verifyOrThrow(
        baseColumns("store_url" to VersionSchemaColumnShape("store_url", "text", null)))
  }

  private fun baseColumns(
      vararg overrides: Pair<String, VersionSchemaColumnShape>,
  ): Map<String, VersionSchemaColumnShape> {
    val defaults =
        linkedMapOf(
            "id" to VersionSchemaColumnShape("id", "bigint", null),
            "main_version" to VersionSchemaColumnShape("main_version", "integer", null),
            "minor_version" to VersionSchemaColumnShape("minor_version", "integer", null),
            "patch_number" to VersionSchemaColumnShape("patch_number", "integer", null),
            "require_update" to VersionSchemaColumnShape("require_update", "boolean", null),
            "store_available" to VersionSchemaColumnShape("store_available", "boolean", null),
            "platform" to VersionSchemaColumnShape("platform", "character varying", 255),
            "service" to VersionSchemaColumnShape("service", "character varying", 255),
            "store_url" to VersionSchemaColumnShape("store_url", "text", null),
            "created_at" to
                VersionSchemaColumnShape("created_at", "timestamp without time zone", null),
        )
    overrides.forEach { (name, column) -> defaults[name] = column }
    return defaults
  }
}
