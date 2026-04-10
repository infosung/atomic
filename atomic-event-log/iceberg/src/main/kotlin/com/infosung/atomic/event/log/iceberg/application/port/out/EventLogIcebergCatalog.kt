package com.infosung.atomic.event.log.iceberg.application.port.out

import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitRequest
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitResult

/**
 * Catalog boundary that performs the actual Iceberg table commit.
 *
 * Implementations must be idempotent by `(tableId, commitId)`.
 *
 * Required behavior:
 * - the first successful commit returns `APPLIED`
 * - a replay with the same logical request returns `ALREADY_COMMITTED`
 * - a replay with the same `(tableId, commitId)` but a different logical file set must fail
 */
fun interface EventLogIcebergCatalog {
  fun commit(request: EventLogIcebergCommitRequest): EventLogIcebergCommitResult
}
