package com.infosung.atomic.contract.database

import java.sql.DatabaseMetaData
import javax.sql.DataSource

/** Loads table index metadata without assuming one vendor-specific catalog query. */
class JdbcTableIndexMetadataLoader(
    private val dataSource: DataSource,
) {
  private val resolvedTableLocator = JdbcResolvedTableLocator()

  fun loadIndexes(tableName: String): Map<String, JdbcTableIndexMetadata> {
    dataSource.connection.use { connection ->
      val metadata = connection.metaData
      val resolvedTable =
          resolvedTableLocator.resolve(metadata, connection.catalog, connection.schema, tableName)
              ?: return emptyMap()

      metadata
          .getIndexInfo(
              resolvedTable.catalog,
              resolvedTable.schema,
              resolvedTable.name,
              false,
              false,
          )
          .use { rs ->
            val indexes = linkedMapOf<String, MutableIndexMetadata>()
            while (rs.next()) {
              val indexName = rs.getString("INDEX_NAME")?.takeIf { it.isNotBlank() } ?: continue
              if (rs.getShort("TYPE").toInt() == DatabaseMetaData.tableIndexStatistic.toInt()) {
                continue
              }
              val columnName = rs.getString("COLUMN_NAME")?.lowercase() ?: continue
              val ordinalPosition = rs.getShort("ORDINAL_POSITION").toInt()
              val normalizedIndexName = indexName.lowercase()
              val index =
                  indexes.getOrPut(normalizedIndexName) {
                    MutableIndexMetadata(
                        name = normalizedIndexName, unique = !rs.getBoolean("NON_UNIQUE"))
                  }
              index.columns += ordinalPosition to columnName
            }
            return indexes.mapValues { (_, index) ->
              JdbcTableIndexMetadata(
                  name = index.name,
                  unique = index.unique,
                  columns = index.columns.sortedBy { it.first }.map { it.second },
              )
            }
          }
    }
  }

  private data class MutableIndexMetadata(
      val name: String,
      val unique: Boolean,
      val columns: MutableList<Pair<Int, String>> = mutableListOf(),
  )
}
