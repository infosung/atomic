package com.infosung.atomic.event.log.parquet.application.model

import java.io.Serializable

/** Flush identity owned by one collector instance. */
data class EventLogParquetExportContext(
    val serverId: String,
    val bootId: String,
    val flushSequence: Long,
) : Serializable

/** One pair of staging/final object keys used during Parquet export. */
data class EventLogParquetObjectKeys(
    val stagingObjectKey: String,
    val finalObjectKey: String,
) : Serializable
