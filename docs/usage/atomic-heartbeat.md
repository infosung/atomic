# atomic.heartbeat Guide

## Why Use This Module

Use `atomic.heartbeat` when your service needs:

- periodic dead-man's-switch style heartbeat ping
- dependency checks (`db`, `redis`) with independent intervals
- dedup policy control across multi-instance deployments (`none`, `leader`, `per-instance`)

## Dependency Pattern

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-heartbeat"))
}
```

## Minimal Setup

```yaml
atomic:
  heartbeat:
    enabled: true
    provider:
      type: healthchecks
      healthchecks:
        base-url: ${ATOMIC_HEARTBEAT_BASE_URL}
```

## Property Reference

### Top-level

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.enabled` | `false` | Enables heartbeat auto-configuration. |
| `atomic.heartbeat.scheduler-thread-prefix` | `atomic-heartbeat` | Prefix for scheduler/check/leader thread names. |

### Ping

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.ping.interval` | `30s` | Heartbeat emit interval. Must be `> 0`. |
| `atomic.heartbeat.ping.send-start-event` | `false` | Sends one `START` event at startup (best-effort). |
| `atomic.heartbeat.ping.fail-open` | `true` | Transport failure handling policy (see behavior table below). |

### Provider

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.provider.type` | `healthchecks` | Provider mode. Supported: `healthchecks`, `custom`. |
| `atomic.heartbeat.provider.connect-timeout` | `1s` | HTTP connect timeout for built-in provider. |
| `atomic.heartbeat.provider.timeout` | `2s` | HTTP request timeout for built-in provider. |
| `atomic.heartbeat.provider.headers` | `{}` | Optional extra HTTP headers. |
| `atomic.heartbeat.provider.instance-id` | `default` | Template variable used by `{instanceId}` replacement. |

### Provider Healthchecks

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.provider.healthchecks.base-url` | `""` | Base ping URL. Required when `provider.type=healthchecks`. |
| `atomic.heartbeat.provider.healthchecks.success-path` | `""` | Success path. Empty means base URL itself. Absolute `http(s)://` URL is used as-is. |
| `atomic.heartbeat.provider.healthchecks.fail-path` | `/fail` | Fail path. Relative value appends to base URL; absolute `http(s)://` URL overrides base URL. |
| `atomic.heartbeat.provider.healthchecks.start-path` | `/start` | Start path. Relative value appends to base URL; absolute `http(s)://` URL overrides base URL. |

### Checks Global

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.checks.missing-bean-policy` | `warn` | Missing dependency behavior for enabled checks. `warn` skips, `fail` aborts startup. |

### DB Check

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.checks.db.enabled` | `false` | Enables DB health check loop. |
| `atomic.heartbeat.checks.db.required` | `true` | Whether DB result gates heartbeat success. |
| `atomic.heartbeat.checks.db.interval` | `30s` | DB check interval. Must be `> 0` when enabled. |
| `atomic.heartbeat.checks.db.timeout` | `2s` | DB check timeout. Must be `> 0` when enabled. |
| `atomic.heartbeat.checks.db.stale-after` | unset | If unset, derived as `interval * 2`. |
| `atomic.heartbeat.checks.db.query` | `SELECT 1` | Validation query. |

### Redis Check

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.checks.redis.enabled` | `false` | Enables Redis health check loop. |
| `atomic.heartbeat.checks.redis.required` | `true` | Whether Redis result gates heartbeat success. |
| `atomic.heartbeat.checks.redis.interval` | `30s` | Redis check interval. Must be `> 0` when enabled. |
| `atomic.heartbeat.checks.redis.timeout` | `2s` | Redis check timeout. Must be `> 0` when enabled. |
| `atomic.heartbeat.checks.redis.stale-after` | unset | If unset, derived as `interval * 2`. |

### Dedup

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.dedup.mode` | `none` | `none`, `leader`, `per-instance`. |

### Dedup Leader

| Property | Default | Description |
|---|---|---|
| `atomic.heartbeat.dedup.leader.backend` | `redis` | `redis`, `jdbc`, `custom`. |
| `atomic.heartbeat.dedup.leader.owner-id` | `""` | Leader owner id. Blank auto-generates process-unique UUID. |
| `atomic.heartbeat.dedup.leader.lease-duration` | `45s` | Lease TTL. Must be `> 0`. |
| `atomic.heartbeat.dedup.leader.renew-interval` | `15s` | Lease renew interval. Must be `> 0` and `< lease-duration`. |
| `atomic.heartbeat.dedup.leader.redis.key` | `atomic:heartbeat:leader` | Lock key for Redis backend. |
| `atomic.heartbeat.dedup.leader.jdbc.table-name` | `atomic_heartbeat_leader` | Lock table name (`[A-Za-z0-9_]+`). |
| `atomic.heartbeat.dedup.leader.jdbc.lock-name` | `default` | Lock row name in the table. |
| `atomic.heartbeat.dedup.leader.jdbc.auto-create-table` | `false` | If `true`, starter tries runtime `CREATE TABLE IF NOT EXISTS`. |

## Behavior Semantics

### `fail-open` and start-event behavior

| Scenario | Behavior |
|---|---|
| `send-start-event=true` and start ping fails | Startup continues. The module does not fail app boot only because start ping failed. |
| `ping.fail-open=true` and runtime ping fails | Transport error is swallowed (best-effort mode). |
| `ping.fail-open=false` and runtime ping fails | Strict mode records internal send-failure state, but scheduler loop still continues. |

### Required check aggregation

- Required checks fail heartbeat when result is unhealthy.
- Required checks also fail heartbeat when result is missing (`no check result`) or stale.
- Check loops and ping loop are independent.

Recommended tuning:

- keep `ping.interval >= max(check timeout)`
- if startup false-fail is sensitive, reduce `required` temporarily during warm-up or increase `ping.interval`

## Dedup Policy

- `none`: all instances emit ping.
- `leader`: only elected leader emits ping.
- `per-instance`: all instances emit ping; use per-instance URL template (`{instanceId}`) to avoid signal collision.

## Leader Contention Flow (`dedup.mode=leader`)

1. Each instance starts lease refresh loop.
2. If instance is current leader, it attempts renew.
3. If renew fails, it attempts acquire.
4. First successful acquire becomes leader.
5. Only leader emits heartbeat.
6. If leader goes down, lease expires and another instance acquires leadership.

Note:

- if leader backend is unavailable, all instances may become non-leader and no heartbeat may be emitted until backend recovers.

## Backend Requirements

- `dedup.mode=none`: no leader backend required.
- `dedup.mode=per-instance`: no leader backend required.
- `dedup.mode=leader` + `backend=redis`: requires `StringRedisTemplate` or `RedisConnectionFactory`.
- `dedup.mode=leader` + `backend=jdbc`: requires `DataSource` + lock table.
- `dedup.mode=leader` + `backend=custom`: requires custom `LeaderElector` bean.

## Custom Mode Requirements

When using custom integration, register required beans explicitly.

```kotlin
import com.infosung.atomic.heartbeat.HeartbeatEvent
import com.infosung.atomic.heartbeat.HeartbeatProvider
import com.infosung.atomic.heartbeat.LeaderElector
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HeartbeatCustomConfig {
  @Bean
  fun heartbeatProvider(): HeartbeatProvider = HeartbeatProvider { event: HeartbeatEvent ->
    // send to custom endpoint/bus
  }

  @Bean
  fun leaderElector(): LeaderElector = object : LeaderElector {
    override fun start() {}
    override fun stop() {}
    override fun isLeader(): Boolean = true
  }
}
```

Important:

- `provider.type=custom` without `HeartbeatProvider` bean fails startup.
- `dedup.leader.backend=custom` without `LeaderElector` bean fails startup.

## JDBC Leader Table (Recommended)

Use migration-managed DDL in production (recommended), and keep `auto-create-table=false`.

```sql
CREATE TABLE IF NOT EXISTS atomic_heartbeat_leader (
  lock_name VARCHAR(128) PRIMARY KEY,
  owner_id VARCHAR(255) NOT NULL,
  lease_until BIGINT NOT NULL
);
```

Operational notes:

- JDBC lease timing uses database `CURRENT_TIMESTAMP` to reduce app clock drift impact.
- runtime auto-create is best for local/dev only; production should use schema migration and explicit DB privileges.

## Operational Checklist

- monitor leader-backend availability (`redis`/`jdbc`) separately from heartbeat target.
- avoid sharing one monitor URL across all instances in `per-instance` mode.
- set `owner-id` explicitly (for example `${HOSTNAME}`) if deterministic owner naming is needed.
- verify `checks.missing-bean-policy` aligns with rollout policy (`warn` for soft rollout, `fail` for strict).
