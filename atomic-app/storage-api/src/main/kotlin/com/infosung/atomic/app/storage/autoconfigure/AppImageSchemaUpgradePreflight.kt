package com.infosung.atomic.app.storage.autoconfigure

import com.infosung.atomic.contract.database.JdbcTableColumnMetadata
import com.infosung.atomic.contract.database.JdbcTableMetadataLoader
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.jdbc.core.JdbcTemplate

/** Fail-fast startup guard for legacy image schema widths. */
class AppImageSchemaUpgradePreflight(
    private val jdbcTemplate: JdbcTemplate,
) : SmartInitializingSingleton {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun afterSingletonsInstantiated() {
    verifyOrThrow()
  }

  fun verifyOrThrow() {
    val requiredColumns = listOf("delete_recovery_claim_token", "delete_recovery_claimed_at")
    val columnsToValidate = listOf("file_name", "thumbnail_file_name", "url", "thumbnail_url")
    val columns = loadColumns(tableName = TABLE_NAME)
    columnsToValidate.forEach { columnName ->
      validateExternallySizedColumn(columns, columnName = columnName)
    }
    requiredColumns.forEach { columnName ->
      validateRequiredColumn(columns, columnName = columnName)
    }
    log.info(
        "Validated image schema upgrade preflight: table={}, checkedColumns={}, checkedPresenceColumns={}",
        TABLE_NAME,
        columnsToValidate,
        requiredColumns,
    )
  }

  private fun loadColumns(tableName: String): Map<String, ColumnShape> {
    val dataSource =
        requireNotNull(jdbcTemplate.dataSource) {
          "AppImageSchemaUpgradePreflight requires a DataSource-backed JdbcTemplate."
        }
    val metadataContext =
        dataSource.connection.use { connection ->
          PreflightMetadataContext(
              productName = connection.metaData.databaseProductName,
              catalog = connection.catalog,
              schema = connection.schema,
          )
        }
    log.info(
        "Running image schema upgrade preflight: databaseProduct={}, catalog={}, schema={}, table={}",
        metadataContext.productName,
        metadataContext.catalog,
        metadataContext.schema,
        tableName,
    )
    val columns = JdbcTableMetadataLoader(dataSource).loadColumns(tableName = tableName)

    if (columns.isEmpty()) {
      val message =
          "Schema upgrade preflight failed: required table '$tableName' was not found " +
              "(databaseProduct=${metadataContext.productName}, catalog=${metadataContext.catalog}, schema=${metadataContext.schema}). " +
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.image."
      log.error(message)
      throw IllegalStateException(message)
    }

    columns.values.forEach { column ->
      log.debug(
          "Loaded image schema column for upgrade preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
          tableName,
          column.name,
          column.jdbcType,
          column.typeName,
          column.columnSize,
      )
    }
    return columns
  }

  private fun validateExternallySizedColumn(
      columns: Map<String, ColumnShape>,
      columnName: String,
  ) {
    val column = columns[columnName]
    if (column == null) {
      val message =
          "Schema upgrade preflight failed: required column '$TABLE_NAME.$columnName' was not found. " +
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.image."
      log.error(message)
      throw IllegalStateException(message)
    }

    if (column.isTextLike() || column.isVariableCharacterAtLeast(MIN_EXTERNAL_LENGTH)) {
      log.debug(
          "Image schema column passed width preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
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
            "Apply an explicit ALTER TABLE migration before enabling atomic.app.image."
    log.error(message)
    throw IllegalStateException(message)
  }

  private fun validateRequiredColumn(
      columns: Map<String, ColumnShape>,
      columnName: String,
  ) {
    val column = columns[columnName]
    if (column != null) {
      log.debug(
          "Image schema column passed presence preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
          TABLE_NAME,
          column.name,
          column.jdbcType,
          column.typeName,
          column.columnSize,
      )
      return
    }

    val message =
        "Schema upgrade preflight failed: required column '$TABLE_NAME.$columnName' was not found. " +
            "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.image."
    log.error(message)
    throw IllegalStateException(message)
  }

  private companion object {
    const val TABLE_NAME: String = "image"
    const val MIN_EXTERNAL_LENGTH: Int = 1024
  }

  private data class PreflightMetadataContext(
      val productName: String?,
      val catalog: String?,
      val schema: String?,
  )
}

private typealias ColumnShape = JdbcTableColumnMetadata
