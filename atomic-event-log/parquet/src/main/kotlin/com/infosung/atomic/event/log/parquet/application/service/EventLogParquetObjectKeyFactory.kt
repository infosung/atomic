package com.infosung.atomic.event.log.parquet.application.service

import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetObjectKeys
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition

/** Deterministic object-key layout for staged and final Parquet files. */
class EventLogParquetObjectKeyFactory(
    rootPrefix: String = DEFAULT_ROOT_PREFIX,
    stagingPrefix: String = DEFAULT_STAGING_PREFIX,
) {
  private val normalizedRootPrefix = normalizePrefix(rootPrefix)
  private val normalizedStagingPrefix = normalizePrefix(stagingPrefix)

  fun create(
      partition: EventLogParquetPartition,
      context: EventLogParquetExportContext,
      fileIndex: Int = 0,
  ): EventLogParquetObjectKeys {
    val flushToken = flushToken(context.flushSequence, fileIndex)
    return EventLogParquetObjectKeys(
        stagingObjectKey =
            listOf(
                    normalizedStagingPrefix,
                    "server_id=${context.serverId}",
                    "boot_id=${context.bootId}",
                    "flush_seq=$flushToken.parquet.tmp",
                )
                .joinToString("/"),
        finalObjectKey =
            listOf(
                    normalizedRootPrefix,
                    "service_id=${partition.serviceId}",
                    "platform=${partition.platform.name.lowercase()}",
                    "dt=${partition.dt}",
                    "hour=${partition.hour.toString().padStart(2, '0')}",
                    "server_id=${context.serverId}",
                    "boot_id=${context.bootId}",
                    "flush_seq=$flushToken.parquet",
                )
                .joinToString("/"),
    )
  }

  private fun flushToken(
      flushSequence: Long,
      fileIndex: Int,
  ): String = if (fileIndex == 0) flushSequence.toString() else "${flushSequence}_$fileIndex"

  private fun normalizePrefix(prefix: String): String = prefix.trim().trim('/')

  private companion object {
    const val DEFAULT_ROOT_PREFIX = "event-log"
    const val DEFAULT_STAGING_PREFIX = "event-log-staging"
  }
}
