package com.infosung.atomic.starter.autoconfigure.heartbeat

import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.heartbeat.DedupMode
import com.infosung.atomic.heartbeat.DependencyCheckPlan
import com.infosung.atomic.heartbeat.DependencyCheckResult
import com.infosung.atomic.heartbeat.DependencyChecker
import com.infosung.atomic.heartbeat.HeartbeatOrchestrator
import com.infosung.atomic.heartbeat.HeartbeatProvider
import com.infosung.atomic.heartbeat.HttpHeartbeatProvider
import com.infosung.atomic.heartbeat.LeaderElector
import com.infosung.atomic.heartbeat.LeaseLeaderElector
import com.infosung.atomic.heartbeat.NoopLeaderElector
import java.sql.Timestamp
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.jdbc.core.JdbcTemplate

/** Auto-configuration for heartbeat ping orchestration and dependency checks. */
@AutoConfiguration
@ConditionalOnClass(name = ["com.infosung.atomic.heartbeat.HeartbeatOrchestrator"])
@ConditionalOnProperty(
    prefix = "atomic.heartbeat",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicHeartbeatProperties::class)
class AtomicHeartbeatAutoConfiguration {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  fun heartbeatPropertiesValidation(properties: AtomicHeartbeatProperties): Any {
    properties.validate()
    return HeartbeatPropertiesValidation
  }

  @Bean
  @ConditionalOnMissingBean
  fun heartbeatProvider(properties: AtomicHeartbeatProperties): HeartbeatProvider {
    if (properties.provider.type == AtomicHeartbeatProperties.Provider.Type.CUSTOM) {
      throw IllegalStateException(
          "atomic.heartbeat.provider.type=custom requires a custom HeartbeatProvider bean.",
      )
    }
    val healthchecks = properties.provider.healthchecks
    val baseUrl = expandTemplate(healthchecks.baseUrl, properties.provider.instanceId)
    return HttpHeartbeatProvider(
        successUrl =
            resolveUrl(
                baseUrl, expandTemplate(healthchecks.successPath, properties.provider.instanceId)),
        failUrl =
            resolveUrl(
                baseUrl, expandTemplate(healthchecks.failPath, properties.provider.instanceId)),
        startUrl =
            resolveUrl(
                baseUrl, expandTemplate(healthchecks.startPath, properties.provider.instanceId)),
        connectTimeout = properties.provider.connectTimeout,
        requestTimeout = properties.provider.timeout,
        headers = properties.provider.headers,
    )
  }

  @Bean
  @ConditionalOnMissingBean
  fun leaderElector(
      properties: AtomicHeartbeatProperties,
      redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
      redisConnectionFactoryProvider: ObjectProvider<RedisConnectionFactory>,
      dataSourceProvider: ObjectProvider<DataSource>,
      customLeaderElectorProvider: ObjectProvider<LeaderElector>,
  ): LeaderElector {
    properties.validate()
    if (properties.dedup.mode != AtomicHeartbeatProperties.Dedup.Mode.LEADER) {
      return NoopLeaderElector()
    }

    val leader = properties.dedup.leader
    return when (leader.backend) {
      AtomicHeartbeatProperties.Dedup.Leader.Backend.REDIS -> {
        val redisTemplate =
            redisTemplateProvider.getIfAvailable()
                ?: redisConnectionFactoryProvider.getIfAvailable()?.let { StringRedisTemplate(it) }
                ?: throw IllegalStateException(
                    "atomic.heartbeat.dedup.mode=leader + backend=redis requires StringRedisTemplate or RedisConnectionFactory.",
                )
        newRedisLeaderElector(
            redisTemplate = redisTemplate,
            key = leader.redis.key,
            ownerId = ownerIdOrDefault(leader.ownerId),
            leaseMillis = leader.leaseDuration.toMillis(),
            renewIntervalMillis = leader.renewInterval.toMillis(),
            threadPrefix = properties.schedulerThreadPrefix,
        )
      }

      AtomicHeartbeatProperties.Dedup.Leader.Backend.JDBC -> {
        val dataSource =
            dataSourceProvider.getIfAvailable()
                ?: throw IllegalStateException(
                    "atomic.heartbeat.dedup.mode=leader + backend=jdbc requires DataSource.",
                )
        newJdbcLeaderElector(
            jdbcTemplate = JdbcTemplate(dataSource),
            tableName = leader.jdbc.tableName,
            lockName = leader.jdbc.lockName,
            ownerId = ownerIdOrDefault(leader.ownerId),
            leaseMillis = leader.leaseDuration.toMillis(),
            renewIntervalMillis = leader.renewInterval.toMillis(),
            autoCreateTable = leader.jdbc.autoCreateTable,
            threadPrefix = properties.schedulerThreadPrefix,
        )
      }

      AtomicHeartbeatProperties.Dedup.Leader.Backend.CUSTOM -> {
        customLeaderElectorProvider.getIfAvailable()
            ?: throw IllegalStateException(
                "atomic.heartbeat.dedup.mode=leader + backend=custom requires custom LeaderElector bean.",
            )
      }
    }
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  @ConditionalOnMissingBean
  fun heartbeatOrchestrator(
      properties: AtomicHeartbeatProperties,
      provider: HeartbeatProvider,
      leaderElector: LeaderElector,
      dataSourceProvider: ObjectProvider<DataSource>,
      redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
      redisConnectionFactoryProvider: ObjectProvider<RedisConnectionFactory>,
      timeProviderProvider: ObjectProvider<TimeProvider>,
  ): HeartbeatOrchestrator {
    val checkPlans =
        buildCheckPlans(
            properties = properties,
            dataSourceProvider = dataSourceProvider,
            redisTemplateProvider = redisTemplateProvider,
            redisConnectionFactoryProvider = redisConnectionFactoryProvider,
        )

    return HeartbeatOrchestrator(
        provider = provider,
        pingIntervalMillis = properties.ping.interval.toMillis(),
        sendStartEvent = properties.ping.sendStartEvent,
        pingFailOpen = properties.ping.failOpen,
        dedupMode =
            when (properties.dedup.mode) {
              AtomicHeartbeatProperties.Dedup.Mode.NONE -> DedupMode.NONE
              AtomicHeartbeatProperties.Dedup.Mode.LEADER -> DedupMode.LEADER
              AtomicHeartbeatProperties.Dedup.Mode.PER_INSTANCE -> DedupMode.PER_INSTANCE
            },
        leaderElector = leaderElector,
        checkPlans = checkPlans,
        timeProvider = timeProviderProvider.getIfAvailable { TimeProvider() },
        schedulerThreadPrefix = properties.schedulerThreadPrefix,
    )
  }

  private fun buildCheckPlans(
      properties: AtomicHeartbeatProperties,
      dataSourceProvider: ObjectProvider<DataSource>,
      redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
      redisConnectionFactoryProvider: ObjectProvider<RedisConnectionFactory>,
  ): List<DependencyCheckPlan> {
    val plans = mutableListOf<DependencyCheckPlan>()
    val missingPolicy = properties.checks.missingBeanPolicy

    if (properties.checks.db.enabled) {
      val dataSource =
          dataSourceProvider.getIfAvailable()
              ?: when (missingPolicy) {
                AtomicHeartbeatProperties.Checks.MissingBeanPolicy.FAIL -> {
                  throw IllegalStateException(
                      "atomic.heartbeat.checks.db.enabled=true requires DataSource bean.")
                }
                AtomicHeartbeatProperties.Checks.MissingBeanPolicy.WARN -> {
                  log.warn(
                      "DB check is enabled but DataSource is missing. DB check will be skipped.")
                  null
                }
              }
      if (dataSource != null) {
        val config = properties.checks.db
        plans +=
            DependencyCheckPlan(
                id = "db",
                checker =
                    JdbcDependencyChecker(
                        dataSource = dataSource,
                        query = config.query,
                        queryTimeoutSeconds = config.timeout.seconds.toInt().coerceAtLeast(1),
                    ),
                required = config.required,
                interval = config.interval,
                timeout = config.timeout,
                staleAfter = config.staleAfter ?: config.interval.multipliedBy(2),
            )
      }
    }

    if (properties.checks.redis.enabled) {
      val redisTemplate =
          redisTemplateProvider.getIfAvailable()
              ?: redisConnectionFactoryProvider.getIfAvailable()?.let { StringRedisTemplate(it) }
              ?: when (missingPolicy) {
                AtomicHeartbeatProperties.Checks.MissingBeanPolicy.FAIL -> {
                  throw IllegalStateException(
                      "atomic.heartbeat.checks.redis.enabled=true requires StringRedisTemplate or RedisConnectionFactory.")
                }
                AtomicHeartbeatProperties.Checks.MissingBeanPolicy.WARN -> {
                  log.warn(
                      "Redis check is enabled but Redis template/factory is missing. Redis check will be skipped.")
                  null
                }
              }
      if (redisTemplate != null) {
        val config = properties.checks.redis
        plans +=
            DependencyCheckPlan(
                id = "redis",
                checker = RedisDependencyChecker(redisTemplate = redisTemplate),
                required = config.required,
                interval = config.interval,
                timeout = config.timeout,
                staleAfter = config.staleAfter ?: config.interval.multipliedBy(2),
            )
      }
    }

    return plans
  }

  private fun newRedisLeaderElector(
      redisTemplate: StringRedisTemplate,
      key: String,
      ownerId: String,
      leaseMillis: Long,
      renewIntervalMillis: Long,
      threadPrefix: String,
  ): LeaderElector {
    val renewScript =
        DefaultRedisScript<Long>().apply {
          resultType = Long::class.java
          setScriptText(
              "if redis.call('get', KEYS[1]) == ARGV[1] " +
                  "then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
          )
        }
    val releaseScript =
        DefaultRedisScript<Long>().apply {
          resultType = Long::class.java
          setScriptText(
              "if redis.call('get', KEYS[1]) == ARGV[1] " +
                  "then return redis.call('del', KEYS[1]) else return 0 end",
          )
        }
    return LeaseLeaderElector(
        renewInterval = java.time.Duration.ofMillis(renewIntervalMillis),
        schedulerThreadName = "$threadPrefix-leader",
        tryAcquire = {
          redisTemplate
              .opsForValue()
              .setIfAbsent(
                  key,
                  ownerId,
                  java.time.Duration.ofMillis(leaseMillis),
              ) == true
        },
        tryRenew = {
          (redisTemplate.execute(
              renewScript,
              listOf(key),
              ownerId,
              leaseMillis.toString(),
          ) ?: 0L) > 0L
        },
        tryRelease = { redisTemplate.execute(releaseScript, listOf(key), ownerId) },
    )
  }

  private fun newJdbcLeaderElector(
      jdbcTemplate: JdbcTemplate,
      tableName: String,
      lockName: String,
      ownerId: String,
      leaseMillis: Long,
      renewIntervalMillis: Long,
      autoCreateTable: Boolean,
      threadPrefix: String,
  ): LeaderElector {
    require(TABLE_NAME_REGEX.matches(tableName)) {
      "atomic.heartbeat.dedup.leader.jdbc.table-name must match [A-Za-z0-9_]+."
    }
    if (autoCreateTable) {
      jdbcTemplate.execute(
          "CREATE TABLE IF NOT EXISTS $tableName (" +
              "lock_name VARCHAR(128) PRIMARY KEY, " +
              "owner_id VARCHAR(255) NOT NULL, " +
              "lease_until BIGINT NOT NULL)",
      )
    }

    fun nowMillis(): Long =
        jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp::class.java)?.time
            ?: throw IllegalStateException(
                "Failed to resolve database current time for heartbeat leader lease.")

    return LeaseLeaderElector(
        renewInterval = java.time.Duration.ofMillis(renewIntervalMillis),
        schedulerThreadName = "$threadPrefix-leader",
        tryAcquire = {
          val now = nowMillis()
          val leaseUntil = now + leaseMillis
          val updated =
              jdbcTemplate.update(
                  "UPDATE $tableName SET owner_id = ?, lease_until = ? " +
                      "WHERE lock_name = ? AND lease_until < ?",
                  ownerId,
                  leaseUntil,
                  lockName,
                  now,
              )
          if (updated > 0) {
            true
          } else {
            try {
              jdbcTemplate.update(
                  "INSERT INTO $tableName(lock_name, owner_id, lease_until) VALUES (?, ?, ?)",
                  lockName,
                  ownerId,
                  leaseUntil,
              )
              true
            } catch (_: DataAccessException) {
              false
            }
          }
        },
        tryRenew = {
          val leaseUntil = nowMillis() + leaseMillis
          jdbcTemplate.update(
              "UPDATE $tableName SET lease_until = ? WHERE lock_name = ? AND owner_id = ?",
              leaseUntil,
              lockName,
              ownerId,
          ) > 0
        },
        tryRelease = {
          jdbcTemplate.update(
              "DELETE FROM $tableName WHERE lock_name = ? AND owner_id = ?",
              lockName,
              ownerId,
          )
        },
    )
  }

  private fun ownerIdOrDefault(ownerId: String): String =
      ownerId.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()

  private fun resolveUrl(baseUrl: String, path: String): String {
    if (path.isBlank()) return baseUrl
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
  }

  private fun expandTemplate(raw: String, instanceId: String): String {
    if (raw.isBlank()) return raw
    return raw.replace("{instanceId}", instanceId)
  }

  private object HeartbeatPropertiesValidation

  companion object {
    private val TABLE_NAME_REGEX = Regex("^[A-Za-z0-9_]+$")
  }
}

private class JdbcDependencyChecker(
    private val dataSource: DataSource,
    private val query: String,
    private val queryTimeoutSeconds: Int,
) : DependencyChecker {
  override fun check(): DependencyCheckResult {
    dataSource.connection.use { connection ->
      connection.prepareStatement(query).use { statement ->
        statement.queryTimeout = queryTimeoutSeconds
        val hasResultSet = statement.execute()
        if (hasResultSet) {
          statement.resultSet?.use {}
        }
      }
    }
    return DependencyCheckResult(healthy = true)
  }
}

private class RedisDependencyChecker(
    private val redisTemplate: StringRedisTemplate,
) : DependencyChecker {
  override fun check(): DependencyCheckResult {
    val pong = redisTemplate.execute { connection -> connection.ping() }
    return if (pong.equals("PONG", ignoreCase = true)) {
      DependencyCheckResult(healthy = true)
    } else {
      DependencyCheckResult(healthy = false, message = "Redis ping returned '$pong'")
    }
  }
}
