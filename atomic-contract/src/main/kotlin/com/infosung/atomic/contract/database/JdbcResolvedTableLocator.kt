package com.infosung.atomic.contract.database

import java.sql.DatabaseMetaData

internal class JdbcResolvedTableLocator {
  fun resolve(
      metadata: DatabaseMetaData,
      catalog: String?,
      schema: String?,
      tableName: String,
  ): JdbcResolvedTable? {
    val tableNameCandidates =
        listOf(
                tableName,
                tableName.lowercase(),
                tableName.uppercase(),
            )
            .distinct()
    val scopeCandidates =
        listOf(
                JdbcTableLookupScope(catalog = catalog, schema = schema),
                JdbcTableLookupScope(catalog = catalog, schema = null),
                JdbcTableLookupScope(catalog = null, schema = schema),
                JdbcTableLookupScope(catalog = null, schema = null),
            )
            .distinct()

    for (candidateTableName in tableNameCandidates) {
      for (scope in scopeCandidates) {
        metadata.getTables(scope.catalog, scope.schema, candidateTableName, TABLE_TYPES).use { rs ->
          if (rs.next()) {
            return JdbcResolvedTable(
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

  private companion object {
    val TABLE_TYPES: Array<String> = arrayOf("TABLE")
  }
}

internal data class JdbcTableLookupScope(
    val catalog: String?,
    val schema: String?,
)

internal data class JdbcResolvedTable(
    val catalog: String?,
    val schema: String?,
    val name: String,
)
