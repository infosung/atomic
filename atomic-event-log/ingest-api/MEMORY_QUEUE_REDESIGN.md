# Atomic Event Log Ingest API Memory Queue Redesign

이 문서는 `atomic-event-log:ingest-api`의 비동기 ingest 구조를
`file-backed intake queue` 중심에서 `in-memory intake queue` 중심으로 바꾸기 위한 설계 메모다.

이 문서는 구현 지시용 설계이자, 메모리 큐 전환에서 어떤 절충안을 채택했는지 남기는 기록이다.

## 배경

기존 비동기 ingest 구조는 다음 특성을 가진다.

- 요청 경로는 `202 ENQUEUED`를 반환한다.
- 하지만 내부적으로는 writer thread가 file queue에 group commit 한 뒤 응답한다.
- queue file은 lane별 append-only file + checkpoint로 유지된다.
- background worker가 queue를 읽어 core ingest를 수행한다.

이 구조의 장점은 crash-safe replay 가능성이다.
하지만 이번 방향 전환에서는 다음 전제를 더 우선한다.

- 목적은 대용량 수집에서 hot path 비용을 줄이는 것
- 갑작스러운 프로세스 crash, 전원 장애, OS 강제 종료까지 커버하는 것은 이번 범위가 아님
- 프로세스 종료는 graceful shutdown 기준으로 본다
- 따라서 `durable intake queue`보다 `fast intake queue`가 우선이다

## 목표

- API path와 request schema는 유지한다.
- 기본 모드는 계속 `ASYNC`이고, `SYNC`는 개발자가 선택 가능하게 유지한다.
- `ASYNC` 모드의 요청 경로에서는 다음만 수행한다.
  - JSON parsing
  - shallow validation
    - `schemaVersion`
    - `serviceId`
    - `event count`
  - authorization
  - ingest context resolution
  - in-memory queue enqueue
- 다음 처리는 요청 경로에서 제외한다.
  - full payload validation
  - masking
  - dedupe
  - normalized record 변환
  - parquet export
  - iceberg publication

## 비목표

- sudden crash 이후 replay 보장
- local WAL / queue file 유지
- ingest queue의 long-term durability
- 이번 문서 단계에서 public API shape 변경

## 새 구조

### 1. receive plane

- Spring MVC controller가 요청을 받는다.
- transport DTO는 `EventLogIngestIntakeBatch`로 shallow mapping 한다.
- 이 시점에는 `platformPayload`를 typed payload로 완전히 변환하지 않는다.
- `ASYNC` 모드에서는 intake batch를 memory queue에 enqueue 한 뒤 바로 응답한다.
- 응답 의미는 여전히 `ENQUEUED`다.

### 2. memory intake plane

- intake queue는 node-local in-memory bounded queue다.
- 기본적으로 `serviceId` 기준 lane 분리를 유지한다.
- 각 lane은 독립 queue를 가진다.
- lane별 queue는 burst 흡수를 위한 bounded buffer다.
- bounded 기준은 request 수와 byte budget을 함께 사용한다.
- queue overflow 시 명시적으로 거절한다.
  - HTTP 응답: `503`
  - code: `EVENT_LOG_INGEST_QUEUE_OVERFLOW`

### 3. process plane

- lane별 worker가 queue item을 drain 한다.
- worker가 intake batch를 `EventLogBatch`로 fully map 한다.
- 이후 core `IngestEventLogUseCase`를 호출한다.
- validation 실패는 handled 로 보고 drop 한다.
- 예기치 않은 예외는 retry 또는 다음 cycle 처리 대상으로 남긴다.

### 4. export plane

- core ingest 결과는 file spool이 아니라 memory spool 또는 memory buffer로 적재한다.
- export scheduler가 memory buffer를 주기적으로 drain 하여 Parquet로 export 한다.
- export 주기와 파일 분할 정책은 ingest와 분리된 설정으로 제어한다.
- ingest-api 모듈은 export scheduler 자체를 직접 소유하지 않는다.
- 따라서 이번 전환의 직접 구현 범위는 intake queue와 graceful shutdown drain 까지로 본다.

## 핵심 결정

### 결정 1. intake queue는 메모리만 사용한다

이유:

- 이번 범위는 graceful shutdown 기준이다.
- crash-safe replay보다 hot path 경량화가 우선이다.
- file queue는 group commit이라도 결국 request path가 writer/fsync에 묶인다.

결과:

- `202 ENQUEUED`는 durable receive가 아니라 memory receive를 의미한다.
- 프로세스 강제 종료 시 queue 내용 유실 가능성을 수용한다.

### 결정 2. file-backed intake queue는 제거 대상이다

현재의 `FileEventLogAsyncIngestQueue` / `HashPartitionedEventLogAsyncIngestQueue`
계열은 새 구조의 기본값이 아니다.

대신 다음과 같은 memory queue 계열이 필요하다.

- `InMemoryEventLogAsyncIngestQueue`
- 필요 시 `HashPartitionedInMemoryEventLogAsyncIngestQueue`

### 결정 3. export 주기 조절이 ingest 안정성의 핵심이다

메모리 기반으로 가면 ingest 단계에서 file append 비용은 빠지지만,
대신 buffer가 오래 쌓이면 메모리 사용량이 커진다.

따라서 export plane은 다음 설정을 가져야 한다.

- `flushInterval`
  - 예: 250ms, 500ms, 1s
- `maxBufferedRecords`
- `maxBufferedBytes`
- `maxRecordsPerFile`
- `maxEstimatedBytesPerFile`
- graceful shutdown 시 `flushOnStop`

즉 기존의 `writeMaxWait` 중심 사고보다
이제는 `buffer flush cadence` 중심으로 운영해야 한다.

### 결정 4. intake queue는 byte budget도 사용한다

request count 기준만 두면 큰 payload 몇 개로도 heap pressure가 먼저 올 수 있다.

따라서 queue 제한은 최소 다음 두 축을 함께 본다.

- `maxBufferedRequestsPerLane`
- `maxBufferedBytesPerLane`

byte size는 추정치이지만, request count만 보는 것보다는 운영상 훨씬 안전하다.

### 결정 5. graceful shutdown은 queue drain까지 보장한다

이 설계에서 shutdown은 단순히 worker thread를 끊는 것이 아니다.

- 새로운 enqueue 중단
- 현재 남아 있는 in-memory queue drain 시도
- 제한 시간 안에서 최대한 처리

unexpected crash replay는 범위 밖이지만,
graceful shutdown에서는 가능한 한 queue를 비우는 방향으로 구현한다.

## API 의미

### ASYNC

- request path: enqueue only
- response: `202 Accepted`
- `processingStatus = ENQUEUED`
- final `ACCEPTED / DUPLICATE / REJECTED`는 즉시 반환하지 않는다

### SYNC

- 기존과 동일하게 full mapping + core ingest를 요청 스레드에서 수행한다
- response: `200 OK`
- `processingStatus = COMPLETED`
- event별 결과를 즉시 반환한다

## graceful shutdown 기준

이번 방향에서 shutdown 의미는 다음이다.

- Spring lifecycle stop 시 새로운 enqueue를 중단한다
- worker drain 을 종료 직전까지 진행한다
- 남아 있는 in-memory normalized buffer를 export 한다
- 종료 timeout 안에 flush가 끝나지 않으면 잔여 메모리 데이터는 유실될 수 있다

즉 이 설계는
`graceful stop 시 최대한 flush`
를 목표로 하고,
`unexpected crash 후 replay`
는 목표로 하지 않는다.

## 남는 tradeoff

### 장점

- request path에서 file append / fsync 제거
- lower tail latency
- higher ingest throughput
- 구조가 단순해짐

### 단점

- crash-safe replay 상실
- queue overflow 시 request drop 가능성 증가
- export scheduler tuning 중요도 증가
- memory pressure 관리 필요
- 같은 lane에 해시되는 서비스 사이의 head-of-line blocking 가능성은 남음

## 구현 방향

### ingest-api

- `EventLogAsyncIngestQueue`는 memory queue 계약으로 재정의한다
- file queue 구현체는 기본 자동설정에서 제거한다
- auto-configuration 기본 queue는 memory queue다
- lane 구조는 유지하되 queue implementation만 메모리로 교체한다
- async 설정은 `queueDirectory`, `writeMaxBatchSize`, `writeMaxWait` 대신
  `maxBufferedRequestsPerLane`, `maxBufferedBytesPerLane`, `shutdownDrainTimeout`
  중심으로 바꾼다

### parquet

- `SpoolBackedEventLogStore + FileEventLogSpool` 조합은 대규모 수집 기본 경로에서 제외한다
- 기본 조합은 `InMemoryEventLogSpool` 또는 별도 `BufferedEventLogStore`로 옮긴다
- export coordinator는 더 짧은 주기의 scheduler와 함께 사용하도록 본다
- 다만 이번 변경의 직접 구현 범위는 ingest-api의 intake queue와 lifecycle 정리까지로 제한한다

### lifecycle

- ingest worker lifecycle과 export lifecycle은 별도 유지 가능
- shutdown 순서는 다음을 권장한다.
  1. enqueue 중단
  2. ingest queue drain
  3. export flush
  4. publication 마감

## 요구사항 요약

- 메모리 큐만 사용
- 기본은 `ASYNC`
- `SYNC`는 계속 선택 가능
- request path는 최대한 가볍게 유지
- abrupt crash durability는 이번 범위 아님
- graceful shutdown 시 flush 전략 필요
- export 주기를 조절 가능한 설정으로 노출

## 절충안 요약

- request durability는 포기하고 ingest latency를 얻는다
- 서비스 간 head-of-line blocking 완화를 위해 lane 구조는 유지한다
- file queue 대신 memory queue로 가되, export scheduler를 더 중요하게 본다
- public API shape는 유지하고 response semantics는 현재 async/sync 모델을 따른다
- queue overflow는 request count와 byte budget을 함께 기준으로 본다
- graceful shutdown에서는 queue drain을 시도하지만, abrupt crash replay는 제공하지 않는다
