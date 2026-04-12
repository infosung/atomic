package com.infosung.atomic.contract.database

import javax.sql.DataSource

/** Loads table column metadata without assuming one vendor-specific catalog query. */
class JdbcTableMetadataLoader(
    private val dataSource: DataSource,
) {
  private val resolvedTableLocator = JdbcResolvedTableLocator()

  fun loadColumns(tableName: String): Map<String, JdbcTableColumnMetadata> {
    dataSource.connection.use { connection ->
      val metadata = connection.metaData
      val resolvedTable =
          resolvedTableLocator.resolve(metadata, connection.catalog, connection.schema, tableName)
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
}
