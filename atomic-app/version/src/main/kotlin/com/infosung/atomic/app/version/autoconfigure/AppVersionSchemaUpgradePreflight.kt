package com.infosung.atomic.app.version.autoconfigure

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.jdbc.core.JdbcTemplate

/** Fail-fast startup guard for legacy service-version schema widths. */
class AppVersionSchemaUpgradePreflight(
    private val jdbcTemplate: JdbcTemplate,
) : SmartInitializingSingleton {
  private val log = LoggerFactory.getLogger(this::class.java)
  private val contract = ServiceVersionSchemaPreflightContract()

  override fun afterSingletonsInstantiated() {
    verifyOrThrow()
  }

  fun verifyOrThrow() {
    val columns = loadColumns(tableName = TABLE_NAME)
    contract.verifyOrThrow(columns)
  }

  private fun loadColumns(tableName: String): Map<String, VersionSchemaColumnShape> {
    val rows =
        jdbcTemplate.query(
            """
            SELECT column_name, data_type, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = ?
            """
                .trimIndent(),
            { rs, _ ->
              VersionSchemaColumnShape(
                  name = rs.getString("column_name"),
                  dataType = rs.getString("data_type"),
                  characterMaximumLength =
                      rs.getObject("character_maximum_length")?.let { (it as Number).toInt() },
              )
            },
            tableName,
        )

    if (rows.isEmpty()) {
      val message =
          "Schema upgrade preflight failed: required table '$tableName' was not found in current schema. " +
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.version."
      log.error(message)
      throw IllegalStateException(message)
    }

    rows.forEach { column ->
      log.debug(
          "Loaded service_version schema column for upgrade preflight: table={}, column={}, dataType={}, characterMaximumLength={}",
          tableName,
          column.name,
          column.dataType,
          column.characterMaximumLength,
      )
    }
    return rows.associateBy { it.name }
  }

  private companion object {
    const val TABLE_NAME: String = "service_version"
  }
}
