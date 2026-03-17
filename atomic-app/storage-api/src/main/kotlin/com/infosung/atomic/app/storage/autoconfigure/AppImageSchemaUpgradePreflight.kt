package com.infosung.atomic.app.storage.autoconfigure

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
    val columnsToValidate = listOf("file_name", "thumbnail_file_name", "url", "thumbnail_url")
    val columns = loadColumns(tableName = TABLE_NAME)
    columnsToValidate.forEach { columnName ->
      validateExternallySizedColumn(columns, columnName = columnName)
    }
    log.info(
        "Validated image schema upgrade preflight: table={}, checkedColumns={}",
        TABLE_NAME,
        columnsToValidate,
    )
  }

  private fun loadColumns(tableName: String): Map<String, ColumnShape> {
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
              ColumnShape(
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
              "Apply the shipped SQL asset or your equivalent migration before enabling atomic.app.image."
      log.error(message)
      throw IllegalStateException(message)
    }

    rows.forEach { column ->
      log.debug(
          "Loaded image schema column for upgrade preflight: table={}, column={}, dataType={}, characterMaximumLength={}",
          tableName,
          column.name,
          column.dataType,
          column.characterMaximumLength,
      )
    }
    return rows.associateBy { it.name }
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

    if (column.isText() || column.isCharacterVaryingAtLeast(MIN_EXTERNAL_LENGTH)) {
      log.debug(
          "Image schema column passed width preflight: table={}, column={}, dataType={}, characterMaximumLength={}",
          TABLE_NAME,
          column.name,
          column.dataType,
          column.characterMaximumLength,
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

  private data class ColumnShape(
      val name: String,
      val dataType: String,
      val characterMaximumLength: Int?,
  ) {
    fun isText(): Boolean = dataType.equals("text", ignoreCase = true)

    fun isCharacterVaryingAtLeast(minLength: Int): Boolean {
      return dataType.equals("character varying", ignoreCase = true) &&
          (characterMaximumLength ?: 0) >= minLength
    }

    fun render(): String {
      return if (characterMaximumLength == null) dataType else "$dataType($characterMaximumLength)"
    }
  }

  private companion object {
    const val TABLE_NAME: String = "image"
    const val MIN_EXTERNAL_LENGTH: Int = 1024
  }
}
