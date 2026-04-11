package com.infosung.atomic.contract.database

import java.sql.DatabaseMetaData
import javax.sql.DataSource

/** Loads table column metadata without assuming one vendor-specific catalog query. */
class JdbcTableMetadataLoader(
    private val dataSource: DataSource,
) {
  fun loadColumns(tableName: String): Map<String, JdbcTableColumnMetadata> {
    dataSource.connection.use { connection ->
      val metadata = connection.metaData
      val resolvedTable = resolveTable(metadata, connection.catalog, connection.schema, tableName)
      if (resolvedTable == null) {
        return emptyMap()
      }

      metadata
          .getColumns(resolvedTable.catalog, resolvedTable.schema, resolvedTable.name, null)
          .use { rs ->
            val columns = linkedMapOf<String, JdbcTableColumnMetadata>()
            while (rs.next()) {
              val column =
                  JdbcTableColumnMetadata(
                      name = rs.getString("COLUMN_NAME"),
                      jdbcType = rs.getInt("DATA_TYPE"),
                      typeName = rs.getString("TYPE_NAME").orEmpty(),
                      columnSize = rs.getObject("COLUMN_SIZE")?.let { (it as Number).toInt() },
                  )
              columns[column.name.lowercase()] = column
            }
            return columns
          }
    }
  }

  private fun resolveTable(
      metadata: DatabaseMetaData,
      catalog: String?,
      schema: String?,
      tableName: String,
  ): ResolvedTable? {
    val tableNameCandidates =
        listOf(
                tableName,
                if (metadata.storesLowerCaseIdentifiers()) tableName.lowercase() else null,
                if (metadata.storesUpperCaseIdentifiers()) tableName.uppercase() else null,
                tableName.lowercase(),
                tableName.uppercase(),
            )
            .filterNotNull()
            .distinct()
    val scopeCandidates =
        listOf(
                Scope(catalog = catalog, schema = schema),
                Scope(catalog = catalog, schema = null),
                Scope(catalog = null, schema = schema),
                Scope(catalog = null, schema = null),
            )
            .distinct()

    for (candidateTableName in tableNameCandidates) {
      for (scope in scopeCandidates) {
        metadata.getTables(scope.catalog, scope.schema, candidateTableName, TABLE_TYPES).use { rs ->
          if (rs.next()) {
            return ResolvedTable(
                catalog = rs.getString("TABLE_CAT"),
                schema = rs.getString("TABLE_SCHEM"),
                name = rs.getString("TABLE_NAME"),
            )
          }
        }
      }
    }
    return null
  }

  private data class Scope(
      val catalog: String?,
      val schema: String?,
  )

  private data class ResolvedTable(
      val catalog: String?,
      val schema: String?,
      val name: String,
  )

  private companion object {
    val TABLE_TYPES: Array<String> = arrayOf("TABLE")
  }
}
