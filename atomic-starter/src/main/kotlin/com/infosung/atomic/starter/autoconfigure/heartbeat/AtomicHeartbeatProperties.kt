package com.infosung.atomic.starter.autoconfigure.heartbeat

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for heartbeat ping orchestration. */
@ConfigurationProperties(prefix = "atomic.heartbeat")
class AtomicHeartbeatProperties {
  var enabled: Boolean = false
  var schedulerThreadPrefix: String = "atomic-heartbeat"
  var ping: Ping = Ping()
  var provider: Provider = Provider()
  var checks: Checks = Checks()
  var dedup: Dedup = Dedup()

  fun validate() {
    require(ping.interval > Duration.ZERO) {
      "atomic.heartbeat.ping.interval must be greater than zero."
    }
    if (provider.type == Provider.Type.HEALTHCHECKS) {
      require(provider.healthchecks.baseUrl.isNotBlank()) {
        "atomic.heartbeat.provider.healthchecks.base-url must not be blank."
      }
      require(provider.timeout > Duration.ZERO) {
        "atomic.heartbeat.provider.timeout must be greater than zero."
      }
      require(provider.connectTimeout > Duration.ZERO) {
        "atomic.heartbeat.provider.connect-timeout must be greater than zero."
      }
    }

    validateCheck("db", checks.db)
    validateCheck("redis", checks.redis)

    if (dedup.mode == Dedup.Mode.LEADER) {
      require(dedup.leader.leaseDuration > Duration.ZERO) {
        "atomic.heartbeat.dedup.leader.lease-duration must be greater than zero."
      }
      require(dedup.leader.renewInterval > Duration.ZERO) {
        "atomic.heartbeat.dedup.leader.renew-interval must be greater than zero."
      }
      require(dedup.leader.renewInterval < dedup.leader.leaseDuration) {
        "atomic.heartbeat.dedup.leader.renew-interval must be smaller than lease-duration."
      }
      if (dedup.leader.backend == Dedup.Leader.Backend.REDIS) {
        require(dedup.leader.redis.key.isNotBlank()) {
          "atomic.heartbeat.dedup.leader.redis.key must not be blank."
        }
      }
      if (dedup.leader.backend == Dedup.Leader.Backend.JDBC) {
        require(TABLE_NAME_REGEX.matches(dedup.leader.jdbc.tableName)) {
          "atomic.heartbeat.dedup.leader.jdbc.table-name must match [A-Za-z0-9_]+."
        }
        require(dedup.leader.jdbc.lockName.isNotBlank()) {
          "atomic.heartbeat.dedup.leader.jdbc.lock-name must not be blank."
        }
      }
    }
  }

  private fun validateCheck(name: String, check: Checks.Check) {
    if (!check.enabled) return
    require(check.interval > Duration.ZERO) {
      "atomic.heartbeat.checks.$name.interval must be greater than zero."
    }
    require(check.timeout > Duration.ZERO) {
      "atomic.heartbeat.checks.$name.timeout must be greater than zero."
    }
    check.staleAfter?.let {
      require(it > Duration.ZERO) {
        "atomic.heartbeat.checks.$name.stale-after must be greater than zero when provided."
      }
    }
  }

  class Ping {
    var interval: Duration = Duration.ofSeconds(30)
    var sendStartEvent: Boolean = false
    var failOpen: Boolean = true
  }

  class Provider {
    var type: Type = Type.HEALTHCHECKS
    var timeout: Duration = Duration.ofSeconds(2)
    var connectTimeout: Duration = Duration.ofSeconds(1)
    var headers: MutableMap<String, String> = mutableMapOf()
    var instanceId: String = "default"
    var healthchecks: Healthchecks = Healthchecks()

    enum class Type {
      HEALTHCHECKS,
      CUSTOM,
    }

    class Healthchecks {
      var baseUrl: String = ""
      var successPath: String = ""
      var failPath: String = "/fail"
      var startPath: String = "/start"
    }
  }

  class Checks {
    var missingBeanPolicy: MissingBeanPolicy = MissingBeanPolicy.WARN
    var db: Db = Db()
    var redis: Redis = Redis()

    enum class MissingBeanPolicy {
      WARN,
      FAIL,
    }

    open class Check {
      var enabled: Boolean = false
      var required: Boolean = true
      var interval: Duration = Duration.ofSeconds(30)
      var timeout: Duration = Duration.ofSeconds(2)
      var staleAfter: Duration? = null
    }

    class Db : Check() {
      var query: String = "SELECT 1"
    }

    class Redis : Check()
  }

  class Dedup {
    var mode: Mode = Mode.NONE
    var leader: Leader = Leader()

    enum class Mode {
      NONE,
      LEADER,
      PER_INSTANCE,
    }

    class Leader {
      var backend: Backend = Backend.REDIS
      var ownerId: String = ""
      var leaseDuration: Duration = Duration.ofSeconds(45)
      var renewInterval: Duration = Duration.ofSeconds(15)
      var redis: Redis = Redis()
      var jdbc: Jdbc = Jdbc()

      enum class Backend {
        REDIS,
        JDBC,
        CUSTOM,
      }

      class Redis {
        var key: String = "atomic:heartbeat:leader"
      }

      class Jdbc {
        var tableName: String = "atomic_heartbeat_leader"
        var lockName: String = "default"
        var autoCreateTable: Boolean = false
      }
    }
  }

  companion object {
    private val TABLE_NAME_REGEX = Regex("^[A-Za-z0-9_]+$")
  }
}
