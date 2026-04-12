package com.infosung.atomic.contract.database

/** Lightweight relational vendor classifier for compatibility and documentation paths. */
enum class RelationalDatabaseDialect {
  POSTGRESQL,
  MYSQL,
  MARIADB,
  H2,
  SQL_SERVER,
  ORACLE,
  UNKNOWN;

  companion object {
    fun detect(databaseProductName: String?): RelationalDatabaseDialect {
      val normalized = databaseProductName?.trim()?.lowercase().orEmpty()
      return when {
        normalized.contains("postgresql") -> POSTGRESQL
        normalized.contains("mariadb") -> MARIADB
        normalized.contains("mysql") -> MYSQL
        normalized == "h2" -> H2
        normalized.contains("sql server") -> SQL_SERVER
        normalized.contains("oracle") -> ORACLE
        else -> UNKNOWN
      }
    }
  }
}
