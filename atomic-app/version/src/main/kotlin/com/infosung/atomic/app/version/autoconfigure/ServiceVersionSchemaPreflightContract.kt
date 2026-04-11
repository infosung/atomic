package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.contract.database.JdbcTableColumnMetadata
import org.slf4j.LoggerFactory

/** Validates the fixed physical contract that app-version runtime queries expect. */
internal class ServiceVersionSchemaPreflightContract {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun verifyOrThrow(columns: Map<String, VersionSchemaColumnShape>) {
    REQUIRED_COLUMNS.forEach { columnName ->
      validateRequiredColumnPresence(columns = columns, columnName = columnName)
    }
    WIDTH_CONTRACT_COLUMNS.forEach { columnName ->
      validateExternallySizedColumn(columns = columns, columnName = columnName)
    }

    log.info(
        "Validated service_version schema upgrade preflight contract: table={}, checkedPresenceColumns={}, checkedWidthColumns={}",
        TABLE_NAME,
        REQUIRED_COLUMNS,
        WIDTH_CONTRACT_COLUMNS,
    )
  }

  private fun validateRequiredColumnPresence(
      columns: Map<String, VersionSchemaColumnShape>,
      columnName: String,
  ): VersionSchemaColumnShape {
    val column = columns[columnName]
    if (column == null) {
      val message =
          "Schema upgrade preflight failed: required column '$TABLE_NAME.$columnName' was not found. " +
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.version."
      log.error(message)
      throw IllegalStateException(message)
    }

    log.debug(
        "service_version schema column passed presence preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
        TABLE_NAME,
        column.name,
        column.jdbcType,
        column.typeName,
        column.columnSize,
    )
    return column
  }

  private fun validateExternallySizedColumn(
      columns: Map<String, VersionSchemaColumnShape>,
      columnName: String,
  ) {
    val column = validateRequiredColumnPresence(columns = columns, columnName = columnName)

    if (column.isTextLike() || column.isVariableCharacterAtLeast(MIN_EXTERNAL_LENGTH)) {
      log.debug(
          "service_version schema column passed width preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
          TABLE_NAME,
          column.name,
          column.jdbcType,
          column.typeName,
          column.columnSize,
      )
      return
    }

    val message =
        "Schema upgrade preflight failed: '$TABLE_NAME.$columnName' is too narrow " +
            "(actual=${column.render()}, required=TEXT or VARCHAR(>=$MIN_EXTERNAL_LENGTH)). " +
            "Apply an explicit ALTER TABLE migration before enabling atomic.app.version."
    log.error(message)
    throw IllegalStateException(message)
  }

  private companion object {
    private val REQUIRED_COLUMNS: List<String> =
        listOf(
            "id",
            "main_version",
            "minor_version",
            "patch_number",
            "require_update",
            "store_available",
            "platform",
            "service",
            "store_url",
            "created_at",
        )
    private val WIDTH_CONTRACT_COLUMNS: List<String> = listOf("store_url")
    private const val TABLE_NAME: String = "service_version"
    private const val MIN_EXTERNAL_LENGTH: Int = 1024
  }
}

internal typealias VersionSchemaColumnShape = JdbcTableColumnMetadata
