# Atomic Event Log Ingest API

`atomic-event-log-ingest-api`는 `atomic-event-log` 코어 위에 공식 HTTP ingest API를 제공하는 선택형 웹 어댑터 모듈이다.

이 모듈은 `atomic-app` 계열과 같은 `application / adapter / autoconfigure` 구조를 따른다.

## 목표

- `atomic-event-log` 코어를 웹 비의존으로 유지한다.
- 서비스 개발자가 컨트롤러, 예외 응답, 기본 요청 규격을 반복 구현하지 않도록 공통 ingest API를 제공한다.
- 기본 수집 경로는 `ASYNC` 메모리 큐 기반으로 가볍게 유지한다.
- 인증, collector 식별, 요청 컨텍스트 추출은 host가 override 가능한 hook으로 둔다.

## 활성화

이 모듈은 기본 비활성화 상태다.

- `atomic.event.log.ingest.enabled=true` 를 켜야 한다.
- 기본 모드는 `ASYNC` 다.
- `atomic.event.log.ingest.mode=ASYNC` 에서는 `EventLogStore` 또는 custom `IngestEventLogUseCase` 가 필요하다.
- `atomic.event.log.ingest.mode=SYNC` 도 동일하게 `EventLogStore` 또는 custom `IngestEventLogUseCase` 가 필요하다.

최소 설정 예시는 다음과 같다.

```yaml
atomic:
  event:
    log:
      ingest:
        enabled: true
        mode: ASYNC
```

그리고 host 애플리케이션은 아래 둘 중 하나를 제공해야 한다.

- `EventLogStore`
- `IngestEventLogUseCase`

## 모듈 경계

- `atomic-event-log`
  - 공통 envelope
  - ingest use case
  - validation / masking / dedupe / append
- `atomic-event-log-ingest-api`
  - HTTP request/response DTO
  - request mapper
  - web controller
  - request authorizer / context resolver hook
  - async intake queue
  - Spring Boot auto-configuration

## 처리 모드

- 기본값: `ASYNC`
- 선택값: `SYNC`

### ASYNC

1. Spring MVC controller가 배치 요청을 받는다.
2. web mapper가 transport DTO를 `EventLogIngestIntakeBatch`로 변환한다.
3. request metadata를 수집한다.
4. `AuthorizeEventLogIngestRequestPort`가 요청을 승인하거나 예외를 던진다.
5. `ResolveEventLogIngestContextPort`가 `collectorId` 같은 ingest context를 만든다.
6. `EventLogAsyncIngestQueue`가 배치를 메모리 intake queue에 enqueue 한다.
7. controller는 `202 Accepted` 와 `ENQUEUED` 상태를 반환한다.
8. background worker가 queue를 읽어 core `IngestEventLogUseCase` 를 호출한다.
9. worker가 `EventLogBatch` 로 fully map 한 뒤 코어가 validation / masking / dedupe / append 를 수행한다.

### SYNC

1. controller가 요청을 받는다.
2. mapper / authorizer / context resolver를 거친다.
3. 코어 `IngestEventLogUseCase` 를 즉시 실행한다.
4. `accepted / duplicate / rejected` 결과를 바로 반환한다.

## Plane 구조

### receive plane

- HTTP parsing
- shallow request mapping
- minimal validation
  - `schemaVersion`
  - `serviceId`
  - `event count`
- authorization
- ingest context resolution
- memory queue enqueue

### process plane

- background worker는 lane별로 독립 drain loop를 가진다.
- worker가 intake batch를 core batch로 fully map 한다.
- worker가 코어 ingest use case를 호출한다.
- validation 실패는 handled 로 보고 해당 entry를 소비한다.
- 예기치 않은 예외는 retry 가능한 상태로 남기고 해당 lane drain 을 중단한다.

### export plane

- `atomic-event-log:parquet` / `iceberg` 조합이 담당한다.
- ingest API는 export 구현과 직접 결합하지 않는다.
- 대용량 운영에서는 request path가 아니라 export scheduler 주기와 buffer 정책을 별도로 조정하는 것이 전제다.

## 기본 정책

- 기본 endpoint: `/api/v1/event-logs:batch`
- 기본 processing mode: `ASYNC`
- 기본 async lane count: `4`
- 기본 authorizer: allow-all
- 기본 context resolver:
  - `receivedAt = Instant.now()`
  - `collectorId` 는 설정된 header 이름이 있을 때만 추출
- 예외 응답은 `BaseResponse` envelope 을 사용한다.
- 기본 `ASYNC` 응답은 `202 Accepted`
- 기본 `SYNC` 응답은 `200 OK`

## 보안 원칙

- 이 모듈은 공식 ingest HTTP API를 제공하지만, 운영 보안 정책 자체를 결정하지는 않는다.
- 기본 authorizer 는 `allow-all` 이므로 개발/로컬 확인용 기본값으로만 보고, 운영에서는 `AuthorizeEventLogIngestRequestPort` 를 반드시 교체하는 것이 전제다.
- host 애플리케이션은 collector key 검증, service allow-list, 인증 사용자 해석, tenant 분리, rate limit, abuse 차단을 직접 구현해야 한다.
- Spring Security 를 사용하는 경우 ingest endpoint 경로를 `SecurityFilterChain` 에 명시적으로 포함하고, anonymous 허용 여부와 CSRF 정책을 host 가 결정해야 한다.
- 사용자 식별이 필요한 경우 client payload 안의 `userId` 류 필드를 신뢰하지 말고, 서버 인증 컨텍스트나 검증된 collector 컨텍스트에서 actor 를 해석하는 편이 맞다.

## ASYNC 설정

`ASYNC` 모드의 기본 queue 는 `serviceId` 해시 기반 lane 분리를 사용한다.

- `laneCount`
  - worker/queue lane 개수
- `maxBufferedRequestsPerLane`
  - lane별 최대 요청 수
- `maxBufferedBytesPerLane`
  - lane별 최대 추정 메모리 사용량
- `enqueueTimeout`
  - queue 포화 시 enqueue 대기 시간
- `workerPollDelay`
  - background drain 주기
- `workerPollLimit`
  - 한 번의 drain 에서 읽는 최대 batch 수
- `shutdownDrainTimeout`
  - graceful shutdown 시 queue drain 허용 시간

## overload 와 shutdown

### queue overflow

- queue 가 request count 또는 byte budget 을 초과하면 enqueue 는 제한 시간까지만 대기한다.
- 제한 시간 안에 자리가 나지 않으면 `503` 과 `EVENT_LOG_INGEST_QUEUE_OVERFLOW` 를 반환한다.

### graceful shutdown

- 종료 시 새로운 enqueue 를 중단한다.
- 남은 queue 는 `shutdownDrainTimeout` 안에서 최대한 drain 한다.
- `shutdownDrainTimeout` 안에 drain 이 끝나지 않으면 남은 batch 는 경고 로그만 남기고 종료될 수 있다.
- 이 모듈은 graceful shutdown 을 지원하지만, 프로세스 강제 종료나 전원 장애 이후 replay 는 보장하지 않는다.

## 확장 포인트

- `AuthorizeEventLogIngestRequestPort`
  - collector key, service allow-list, host 인증 연동
- `ResolveEventLogIngestContextPort`
  - collector id, host metadata, trace bridge
- `IngestEventLogUseCase`
  - host가 자체 ingest use case를 넣고 싶을 때 override
- `EventLogAsyncIngestQueue`
  - host가 queue 구현을 직접 교체하고 싶을 때 override
- `EventLogAsyncIngestWorkerLifecycle`
  - host가 background drain lifecycle 을 교체하고 싶을 때 override

## 절충안

- 요청 규격은 공통 batch envelope 하나로 고정한다.
- 플랫폼 reserved payload는 transport 에서는 JSON object 로 받고, mapper 가 typed payload 로 변환한다.
- 비즈니스 payload는 opaque scalar map 으로만 받는다.
- malformed JSON 은 `400` 으로 응답한다.
- `ASYNC` 기본 응답은 최종 `ACCEPTED` 가 아니라 `ENQUEUED` 다.
- `SYNC` 는 개발자가 명시적으로 선택해야 한다.
- async durability 대신 request-path 경량화를 선택한다.
- abrupt crash replay 는 범위 밖으로 두고, graceful shutdown drain 만 기본 제공한다.

## 남는 운영 리스크

- 같은 lane 으로 해시되는 서비스끼리는 head-of-line blocking 가능성이 남는다.
- queue budget 은 request count 와 byte estimate 기반이므로, 실제 heap 사용량과 완전히 일치하지는 않는다.
- export scheduler 튜닝이 따라오지 않으면 ingest 는 빨라도 downstream flush 가 병목이 될 수 있다.
