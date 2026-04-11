package com.infosung.atomic.contract.database

import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JdbcRelationalDatabaseSupportTest {
  @Test
  fun `dialect detector should recognize common relational vendors`() {
    assertEquals(
        RelationalDatabaseDialect.POSTGRESQL, RelationalDatabaseDialect.detect("PostgreSQL"))
    assertEquals(RelationalDatabaseDialect.MYSQL, RelationalDatabaseDialect.detect("MySQL"))
    assertEquals(RelationalDatabaseDialect.MARIADB, RelationalDatabaseDialect.detect("MariaDB"))
    assertEquals(RelationalDatabaseDialect.H2, RelationalDatabaseDialect.detect("H2"))
    assertEquals(
        RelationalDatabaseDialect.SQL_SERVER,
        RelationalDatabaseDialect.detect("Microsoft SQL Server"),
    )
    assertEquals(RelationalDatabaseDialect.ORACLE, RelationalDatabaseDialect.detect("Oracle"))
    assertEquals(RelationalDatabaseDialect.UNKNOWN, RelationalDatabaseDialect.detect("CustomDb"))
  }

  @Test
  fun `column metadata should treat large text-like types as unbounded text`() {
    assertTrue(
        JdbcTableColumnMetadata(
                name = "store_url",
                jdbcType = Types.LONGVARCHAR,
                typeName = "LONGTEXT",
                columnSize = 16_777_215,
            )
            .isTextLike(),
    )
    assertTrue(
        JdbcTableColumnMetadata(
                name = "payload_json",
                jdbcType = Types.CLOB,
                typeName = "CLOB",
                columnSize = null,
            )
            .isTextLike(),
    )
    assertFalse(
        JdbcTableColumnMetadata(
                name = "platform",
                jdbcType = Types.VARCHAR,
                typeName = "VARCHAR",
                columnSize = 255,
            )
            .isTextLike(),
    )
  }

  @Test
  fun `column metadata should validate varchar width across vendors`() {
    assertTrue(
        JdbcTableColumnMetadata(
                name = "store_url",
                jdbcType = Types.VARCHAR,
                typeName = "VARCHAR",
                columnSize = 2048,
            )
            .isVariableCharacterAtLeast(1024),
    )
    assertFalse(
        JdbcTableColumnMetadata(
                name = "store_url",
                jdbcType = Types.VARCHAR,
                typeName = "VARCHAR",
                columnSize = 255,
            )
            .isVariableCharacterAtLeast(1024),
    )
  }
}
