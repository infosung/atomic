package com.infosung.atomic.app.version.autoconfigure

import com.infosung.atomic.contract.database.JdbcTableMetadataLoader
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
    val dataSource =
        requireNotNull(jdbcTemplate.dataSource) {
          "AppVersionSchemaUpgradePreflight requires a DataSource-backed JdbcTemplate."
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
        "Running service_version schema upgrade preflight: databaseProduct={}, catalog={}, schema={}, table={}",
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
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.version."
      log.error(message)
      throw IllegalStateException(message)
    }

    columns.values.forEach { column ->
      log.debug(
          "Loaded service_version schema column for upgrade preflight: table={}, column={}, jdbcType={}, typeName={}, columnSize={}",
          tableName,
          column.name,
          column.jdbcType,
          column.typeName,
          column.columnSize,
      )
    }
    return columns
  }

  private companion object {
    const val TABLE_NAME: String = "service_version"
  }

  private data class PreflightMetadataContext(
      val productName: String?,
      val catalog: String?,
      val schema: String?,
  )
}
