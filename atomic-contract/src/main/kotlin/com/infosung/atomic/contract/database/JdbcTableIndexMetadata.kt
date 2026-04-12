package com.infosung.atomic.contract.database

/** Portable index metadata extracted from JDBC `DatabaseMetaData`. */
data class JdbcTableIndexMetadata(
    val name: String,
    val unique: Boolean,
    val columns: List<String>,
)
