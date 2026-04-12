# atomic.event.log Lakehouse Guide

This guide explains how the event-log storage side is intended to be composed in `0.1.1`.

The current stack supports three operational modes:

1. `Parquet-only`
2. `Iceberg + HadoopCatalog`
3. `Iceberg + REST catalog`

Recommended rollout order:

1. start with `Parquet-only`
2. add `Iceberg + HadoopCatalog` when you want catalog-managed tables without another service
3. move to `Iceberg + REST catalog` when you need shared multi-service catalog infrastructure

## 1. What Each Module Owns

| Module | Owns |
|---|---|
| `atomic.event.log.parquet` | spool/store boundary, partition calculation, deterministic object keys, export coordinator, publication strategy contract |
| `atomic.event.log.iceberg` | catalog commit contract, table strategy contract, publication strategy for Iceberg commits |
| `atomic.event.log.duckdb` | SQL rendering for raw Parquet, Iceberg scans, and GA-compatible client analytics |

## 2. Storage Modes

### `Parquet-only`

Use when:
- you want the smallest operational footprint
- files are enough for your downstream jobs
- DuckDB can read directly from the Parquet layout

What happens:
- records accumulate in the spool
- `EventLogParquetExportCoordinator` groups them into files
- `ParquetOnlyEventLogPublicationStrategy` treats promoted Parquet files as the terminal artifact

### `Iceberg + HadoopCatalog`

Use when:
- you want Iceberg table semantics
- you do not want to run a separate catalog service yet

What happens:
- Parquet files are still the physical data files
- after promotion, the Iceberg publication strategy commits those files into tables through a
  host-provided `EventLogIcebergCatalog`
- the catalog is backed by a filesystem/object-store based Hadoop catalog layout

### `Iceberg + REST catalog`

Use when:
- you already run a central catalog service
- multiple services or environments share the same catalog boundary
- operational ownership of the catalog is separate from collector nodes

What happens:
- the publication step is the same shape as HadoopCatalog
- only the actual catalog implementation behind `EventLogIcebergCatalog` changes

## 3. Partition Layout

The current Parquet partition key is:

- `serviceId`
- `platform`
- `dt`
- `hour`

The default object-key factory writes final objects under:

```text
event-log/
  service_id=<serviceId>/
  platform=<platform>/
  dt=<yyyy-MM-dd>/
  hour=<00-23>/
  server_id=<serverId>/
  boot_id=<bootId>/
  flush_seq=<flushSequence>.parquet
```

Examples:

- `event-log/service_id=fillingheart/platform=client_desktop/dt=2026-04-12/hour=08/server_id=collector-a/boot_id=boot-1/flush_seq=42.parquet`
- `event-log/service_id=totp/platform=api/dt=2026-04-12/hour=08/server_id=collector-b/boot_id=boot-9/flush_seq=103.parquet`

This layout is important because:
- one collector can handle multiple services cleanly
- DuckDB raw scans can filter by `service_id=` and `platform=`
- Iceberg partition values can be derived deterministically from the same keys

## 4. Export Cadence

`EventLogParquetExportCoordinator` is a host-driven batch export coordinator.

It does not own scheduling by itself.

The host decides:
- export interval
- flush sequence generation
- `serverId` and `bootId`
- whether to stop after a time budget
- file size goals through `EventLogParquetExportPolicy`

Current defaults:

- `maxPendingDrain = 5000`
- `maxRecordsPerFile = 5000`
- `maxEstimatedBytesPerFile = Long.MAX_VALUE`
- `maxFlushDuration = null`

Interpretation:
- the coordinator drains at most 5000 pending spool entries per export call
- file rotation happens when the record limit or estimated byte budget is reached

## 5. Spool Choices

### `InMemoryEventLogSpool`

Use when:
- your collector path is intentionally memory-first
- you only need graceful shutdown drain, not crash replay
- the host can tolerate losing not-yet-exported records on abrupt process termination

### `FileEventLogSpool`

Use when:
- you explicitly want a replayable spool boundary
- you accept the operational cost of file-backed append/checkpoint handling

Important limitation:
- `FileEventLogSpool` only protects records after they reach store append
- in the default `ASYNC` HTTP collector path, there is still an in-memory queue before the worker
  writes anything into the spool
- that means `ASYNC + FileEventLogSpool` is not a fully crash-safe receive path
- if your rollout requires request-time append semantics, use `SYNC` with a file-backed store/spool
  and document the throughput tradeoff explicitly

In the current `0.1.1` collector line, `InMemoryEventLogSpool` is the lighter default recommendation
for general intake servers. `FileEventLogSpool` remains an explicit host choice, not the default
collector recommendation.

## 6. What the Host Still Has to Implement

The biggest remaining host seam is `EventLogParquetFileRepository`.

Atomic gives you:
- partition planning
- object-key planning
- file publication lifecycle

The host still implements:
- how actual Parquet bytes are written
- where staging files live
- how promotion works
- how object metadata is preserved

That means the lakehouse guide should be read as a composition guide, not as “drop-in full storage
server with zero host code”.

## 7. Iceberg Commit Contract

`EventLogIcebergCatalog` is the catalog boundary.

It must be idempotent by:

- `tableId`
- `commitId`

Required behavior from the contract:

- first successful commit -> `APPLIED`
- replay of the same logical request -> `ALREADY_COMMITTED`
- same `(tableId, commitId)` with different file set -> fail

This is what lets publication retry safely without duplicating data files at the table layer.

## 8. DuckDB Read Paths

`atomic.event.log.duckdb` does not run DuckDB for you.
It renders SQL so the host can query:

- raw Parquet datasets
- Iceberg metadata files
- attached Iceberg catalogs
- GA-compatible client analytics views

### Raw Parquet query example

```kotlin
val renderer = EventLogDuckDbSqlRenderer()
val sql =
    renderer.renderParquetQuery(
        dataset = EventLogDuckDbParquetDataset(rootUri = "s3://analytics-bucket/event-log"),
        filter = EventLogDuckDbFilter(serviceId = "fillingheart"),
    )
```

### Iceberg attach example

```kotlin
val renderer = EventLogDuckDbSqlRenderer()
val attachSql =
    renderer.renderIcebergAttach(
        EventLogDuckDbIcebergCatalog(
            alias = "analytics",
            source = "warehouse",
            endpoint = "https://catalog.example.com",
        ))
```

## 9. GA-Compatible Client View

For client analytics, the most important helper is:

- `client_ga4_compat_events_v1`

`EventLogDuckDbSqlRenderer` can create this view over Parquet or Iceberg data.

The view exposes GA-style columns such as:

- `event_date`
- `event_timestamp_micros`
- `user_id`
- `user_pseudo_id`
- `session_id`
- `engagement_time_msec`
- `app_id`
- `app_version`
- `screen_name`
- `release_channel`

That view is only useful when client payloads keep the reserved fields documented in
[atomic.event.log Client Guide](atomic-event-log-client.md).

## 10. Built-In DuckDB Query Helpers

The current canned client queries include:

- daily active users
- active installs
- session count
- screen view count
- release adoption
- error count
- engagement time

Important conventions:
- screen-view query assumes `event_name = 'screen_view'`
- error query assumes `event_type = 'ERROR'` or `event_name = 'app_exception'`

## 11. Multi-Service Analytics

The shared collector can ingest multiple products in the same storage root.

That is why `serviceId` should always be present and stable.

Examples:
- `fillingheart`
- `totp`
- `desktop-launcher`

The same DuckDB root can then be queried per service with:

- `serviceId = "fillingheart"`
- `serviceId = "totp"`

This keeps one lakehouse path available for all products without giving up service boundaries.

## 12. Practical Rollout Recommendation

### Phase 1

- `InMemoryEventLogSpool`
- `Parquet-only`
- one export scheduler
- DuckDB direct Parquet reads

### Phase 2

- keep the same Parquet layout
- add `Iceberg + HadoopCatalog`
- preserve DuckDB query ability through either direct Parquet or Iceberg view creation

### Phase 3

- switch publication to `Iceberg + REST catalog`
- keep client reserved fields unchanged
- keep `serviceId` partitioning unchanged so historical paths and queries stay consistent
