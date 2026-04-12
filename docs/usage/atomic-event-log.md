# atomic.event.log Guide

This guide explains how `0.1.0` should be used to compose an event-log collector server.

Important scope note:
- `atomic.event.log` is a library stack, not a standalone executable collector.
- The official HTTP ingest endpoint is provided by `atomic.event.log.ingest.api`.
- The host application still owns security, runtime wiring, scheduler ownership, and the concrete
  Parquet file repository implementation.

Recommended companion guides:
- Collector-side lakehouse/export/read path: [atomic.event.log Lakehouse Guide](atomic-event-log-lakehouse.md)
- Client envelope and batching conventions: [atomic.event.log Client Guide](atomic-event-log-client.md)

## 1. Module Map

| Module | Responsibility |
|---|---|
| `atomic.event.log` | Common envelope, validation, masking, dedupe, and append contract |
| `atomic.event.log.ingest.api` | Official Spring MVC ingest API with async memory-queue intake |
| `atomic.event.log.parquet` | Spool-backed store, partition/object-key policy, export coordinator, and publication strategy contract |
| `atomic.event.log.iceberg` | Iceberg commit and table strategy contracts |
| `atomic.event.log.duckdb` | DuckDB SQL renderer for raw and GA-compatible client analytics queries |
| `atomic.event.log.spring.web` | Adapter from `atomic.spring.web` API logs into the common event-log envelope |

## 2. What Runs in the Request Path

The current recommended collector path is intentionally split.

### `ASYNC` request path

In `ASYNC`, the controller does only the following before returning:

1. Parse JSON.
2. Run shallow request validation.
3. Run request authorization and context resolution.
4. Enqueue the intake batch into the bounded in-memory async ingest queue.
5. Return `202 Accepted` with `processingStatus=ENQUEUED`.

This means:
- `202 Accepted` does not mean the batch has already been validated, deduplicated, or exported.
- `202 Accepted` means the request survived shallow validation and was accepted by the in-memory
  intake queue.
- enqueue is not unbounded; if the target lane is saturated the server can wait briefly up to
  `enqueueTimeout` and then reject with `503` / `EVENT_LOG_INGEST_QUEUE_OVERFLOW`
- A sudden process crash can still lose queued batches that were not yet processed.

### Background process plane

After enqueue:

1. `ProcessQueuedEventLogBatchesService` drains each lane.
2. `EventLogIngestRequestMapper` converts the shallow intake model into the fully typed core batch.
3. `EventLogIngestionService` performs full validation, payload masking, dedupe, and append.
4. The configured `EventLogStore` persists accepted records into the chosen spool/store boundary.

### Export plane

Export is explicitly outside the HTTP request path.

1. A host-owned scheduler calls `EventLogParquetExportCoordinator`.
2. Pending spool entries are grouped by `(serviceId, platform, dt, hour)`.
3. A host-provided `EventLogParquetFileRepository` stages and promotes Parquet files.
4. Publication finishes in one of three modes:
   - `Parquet-only`
   - `Iceberg + HadoopCatalog`
   - `Iceberg + REST catalog`

### Important durability boundary

The async intake queue sits before the store/spool boundary.

That means:
- `ASYNC + InMemoryEventLogSpool` is memory-only until background ingest and later export happen
- `ASYNC + FileEventLogSpool` still has a pre-worker loss window, because the HTTP request returns
  after queue enqueue, not after file append
- if you need request-time append semantics, the host must explicitly choose `SYNC` and a
  file-backed store/spool composition, accepting the throughput tradeoff

## 3. Supported Platforms and Payload Families

The shared event envelope supports these platform families:

- `API`
- `WEBSOCKET`
- `CLIENT_WEB`
- `CLIENT_MOBILE`
- `CLIENT_TABLET`
- `CLIENT_IPAD`
- `CLIENT_DESKTOP`
- `SERVER`

Reserved platform payload types are different on purpose:

- API logs use `ApiEventLogPayload`
- WebSocket logs use `WebSocketEventLogPayload`
- Client logs use `ClientEventLogPayload`
- Server logs use `ServerEventLogPayload`

`businessPayload` is always an opaque scalar map:
- allowed value types: string, integer, decimal, boolean
- nested objects or arrays are rejected by the HTTP mapper

## 4. Minimal Collector Dependencies

Published artifact example:

```kotlin
dependencies {
  implementation("com.infosung:atomic.event.log:0.1.0")
  implementation("com.infosung:atomic.event.log.parquet:0.1.0")
  implementation("com.infosung:atomic.event.log.ingest.api:0.1.0")
}
```

If you already use `atomic.spring.web` and want to forward existing API logs into the same
collector pipeline, add:

```kotlin
implementation("com.infosung:atomic.event.log.spring.web:0.1.0")
```

Important:
- the bridge is not auto-wired just by adding the dependency
- the host still has to register `AtomicSpringWebEventLogSaver` as the `LogSaver`
- the existing `ApiLogAspect` capture layer remains the producer of request/response logs

## 5. Minimal Spring Boot Activation

```yaml
atomic:
  event:
    log:
      ingest:
        enabled: true
        endpoint-path: /api/v1/event-logs:batch
        mode: ASYNC
```

Key facts:
- default endpoint is `/api/v1/event-logs:batch`
- default mode is `ASYNC`
- default lane count is `4`
- default queue authorizer is allow-all

## 6. Host Wiring Responsibilities

`atomic.event.log.ingest.api` can expose the HTTP API, but the host still has to provide the core
composition.

### Minimum beans

At minimum, the host should provide one of:

- `EventLogStore`
- `IngestEventLogUseCase`

The most direct composition uses:

- `InMemoryEventLogSpool` for current memory-first collector deployments
- `SpoolBackedEventLogStore` as the core append target
- `EventLogIngestionService` as the core ingest use case

Example wiring:

```kotlin
@Configuration
class EventLogCollectorConfiguration {
  @Bean
  fun eventLogSpool(): EventLogSpool = InMemoryEventLogSpool()

  @Bean
  fun eventLogStore(spool: EventLogSpool): EventLogStore = SpoolBackedEventLogStore(spool = spool)

  @Bean
  fun ingestEventLogUseCase(store: EventLogStore): IngestEventLogUseCase =
      EventLogIngestionService(store = store)
}
```

### Parquet export wiring

`atomic.event.log.parquet` ships the export coordinator and key/partition policy, but it does not
ship a concrete `EventLogParquetFileRepository` implementation in `0.1.0`.

That means the host must still implement:

- how records are written into staged Parquet files
- where staged files live
- how staged files are promoted to final object keys

Typical composition looks like:

```kotlin
@Bean
fun parquetExportCoordinator(
    spool: EventLogSpool,
    parquetFileRepository: EventLogParquetFileRepository,
): EventLogParquetExportCoordinator =
    EventLogParquetExportCoordinator(
        spool = spool,
        partitioner = EventLogParquetPartitioner(),
        keyFactory = EventLogParquetObjectKeyFactory(),
        repository = parquetFileRepository,
    )
```

Then the host owns the scheduler:

```kotlin
@Component
class EventLogExportScheduler(
    private val coordinator: EventLogParquetExportCoordinator,
) {
  @Scheduled(fixedDelayString = "\${app.event-log.export.fixed-delay:5000}")
  fun export() {
    coordinator.export(
        EventLogParquetExportContext(
            serverId = "collector-a",
            bootId = "boot-20260412",
            flushSequence = System.currentTimeMillis(),
        ))
  }
}
```

### `atomic.spring.web` bridge wiring

If your service already emits `ServiceLog` entries through `atomic.spring.web`, the bridge path is:

1. keep your existing `ApiLogAspect`
2. register `AtomicSpringWebEventLogSaver` as the `LogSaver`
3. point it at the same `EventLogIngestionService`

The dependency alone does not start forwarding API logs automatically.

## 7. HTTP API Contract

### Request envelope

```json
{
  "schemaVersion": 1,
  "serviceId": "totp",
  "events": [
    {
      "eventId": "evt-1",
      "eventName": "api.request",
      "eventType": "REQUEST",
      "occurredAt": "2026-04-10T00:00:00Z",
      "platform": "API",
      "actorId": "user-1",
      "traceId": "trace-1",
      "tags": ["public"],
      "platformPayload": {
        "httpMethod": "POST",
        "endpoint": "/api/v1/totp",
        "status": 200,
        "executeTimeMs": 42
      },
      "businessPayload": {
        "issuer": "github",
        "retry": false
      }
    }
  ]
}
```

### `ASYNC` response

Status: `202 Accepted`

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "serviceId": "totp",
    "schemaVersion": 1,
    "processingMode": "ASYNC",
    "processingStatus": "ENQUEUED",
    "receiptId": "receipt-1",
    "queuedAt": "2026-04-10T00:00:01Z",
    "queuedEventCount": 1
  }
}
```

### `SYNC` response

Status: `200 OK`

`SYNC` waits for full core ingest to finish and returns:

- `accepted`
- `duplicate`
- `rejected`
- per-event result rows

Use `SYNC` only when the host explicitly wants request-time ingestion semantics.

## 8. ASYNC Queue Model

The current queue implementation is in-memory and lane-partitioned by `serviceId`.

- `laneCount > 1` creates `HashPartitionedInMemoryEventLogAsyncIngestQueue`
- lane routing is `floorMod(serviceId.hashCode(), laneCount)`
- each lane keeps its own request count and byte budget
- each lane is drained independently by the background worker lifecycle

This is not a single `HashMap` of events.
It is a small set of independent in-memory queues, and `serviceId` hashing decides which lane a
batch enters.

Default queue budget:

- `maxBufferedRequestsPerLane = 1024`
- `maxBufferedBytesPerLane = 16 MiB`
- `enqueueTimeout = 10 ms`
- `workerPollDelay = 100 ms`
- `workerPollLimit = 256`
- `shutdownDrainTimeout = 30 s`

If a lane is saturated:
- the controller waits up to `enqueueTimeout`
- if capacity does not free up, the API returns `503`
- error code is `EVENT_LOG_INGEST_QUEUE_OVERFLOW`

## 9. Security Boundary

Security remains a host responsibility.

What the library does:
- expose a standard HTTP contract
- call `AuthorizeEventLogIngestRequestPort`
- expose a context hook through `ResolveEventLogIngestContextPort`

What the host must still decide:
- collector authentication and authorization
- service allow-listing
- tenant isolation
- network exposure policy
- rate limiting and abuse controls
- whether client-supplied `actorId` should be trusted at all

Production guidance:
- replace the default allow-all `AuthorizeEventLogIngestRequestPort`
- gate the endpoint in Spring Security
- decide CSRF policy explicitly
- prefer server-validated identity over client-claimed identity
- do not treat the collector as a public anonymous endpoint by default

## 10. Multi-Service Separation

`serviceId` is the first-level namespace for shared collector usage.

Examples:
- `fillingheart`
- `totp`
- `desktop-launcher`

Each batch belongs to one `serviceId`, and downstream storage is partitioned by that service.
That lets one collector server handle logs from multiple products without mixing their lakehouse
paths.

## 11. Recommended Default for `0.1.0`

For a new collector host, the safest starting point is:

1. `atomic.event.log` + `atomic.event.log.parquet` + `atomic.event.log.ingest.api`
2. `ASYNC` ingest mode
3. `InMemoryEventLogSpool`
4. one host-owned export scheduler
5. `Parquet-only` publication first
6. `DuckDB` only after files are landing correctly
7. `Iceberg` only when multi-table publication or catalog-managed reads are required

## 12. Known Operational Boundaries

- `ASYNC` queue data is memory-only until the background worker processes it.
- abrupt process termination can lose not-yet-processed queued batches.
- Parquet export is not automatic just because ingest works; the host must own scheduler cadence.
- `EventLogParquetFileRepository` is still a host seam in `0.1.0`.
- if export falls behind, ingestion may still succeed while downstream analysis becomes stale.
