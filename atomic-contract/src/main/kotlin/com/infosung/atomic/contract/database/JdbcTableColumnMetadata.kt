package com.infosung.atomic.contract.database

import java.sql.Types

/** Portable column metadata extracted from JDBC `DatabaseMetaData`. */
data class JdbcTableColumnMetadata(
    val name: String,
    val jdbcType: Int,
    val typeName: String,
    val columnSize: Int?,
) {
  fun isTextLike(): Boolean {
    return jdbcType in TEXT_JDBC_TYPES ||
        typeName.uppercase().let { normalized ->
          normalized.contains("TEXT") || normalized.contains("CLOB")
        }
  }

  fun isVariableCharacterAtLeast(minLength: Int): Boolean {
    return jdbcType in VARIABLE_CHARACTER_JDBC_TYPES && (columnSize ?: 0) >= minLength
  }

  fun render(): String {
    val renderedType = typeName.ifBlank { jdbcType.toString() }.uppercase()
    return if (columnSize == null || isTextLike()) renderedType else "$renderedType($columnSize)"
  }

  private companion object {
    val TEXT_JDBC_TYPES: Set<Int> =
        setOf(
            Types.CLOB,
            Types.NCLOB,
            Types.LONGVARCHAR,
            Types.LONGNVARCHAR,
        )

    val VARIABLE_CHARACTER_JDBC_TYPES: Set<Int> =
        setOf(
            Types.CHAR,
            Types.NCHAR,
            Types.VARCHAR,
            Types.NVARCHAR,
            Types.LONGVARCHAR,
            Types.LONGNVARCHAR,
        )
  }
}
