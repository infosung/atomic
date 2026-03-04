package com.infosung.atomic.heartbeat

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generic lease-based leader elector.
 *
 * Ownership is maintained by periodic renew calls. On renew failure, acquisition is retried.
 */
class LeaseLeaderElector(
    private val renewInterval: Duration,
    private val schedulerThreadName: String,
    private val tryAcquire: () -> Boolean,
    private val tryRenew: () -> Boolean,
    private val tryRelease: () -> Unit,
) : LeaderElector {
  private val log = System.getLogger(LeaseLeaderElector::class.java.name)
  private val leader = AtomicBoolean(false)
  private val running = AtomicBoolean(false)

  @Volatile private var scheduler: ScheduledExecutorService? = null
  @Volatile private var future: ScheduledFuture<*>? = null

  override fun start() {
    if (!running.compareAndSet(false, true)) return
    val newScheduler =
        Executors.newSingleThreadScheduledExecutor { runnable ->
          Thread(runnable, schedulerThreadName).apply { isDaemon = true }
        }
    scheduler = newScheduler
    refreshLeadership()
    future =
        newScheduler.scheduleWithFixedDelay(
            { refreshLeadership() },
            renewInterval.toMillis(),
            renewInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return
    future?.cancel(true)
    future = null
    runCatching {
          if (leader.get()) {
            tryRelease()
          }
        }
        .onFailure {
          log.log(
              System.Logger.Level.WARNING,
              "Failed to release heartbeat leader lease during stop",
              it,
          )
        }
    leader.set(false)
    scheduler?.shutdownNow()
    scheduler = null
  }

  override fun isLeader(): Boolean = leader.get()

  private fun refreshLeadership() {
    if (!running.get()) return
    val stillLeader =
        if (leader.get()) {
          runCatching { tryRenew() }
              .onFailure {
                log.log(
                    System.Logger.Level.WARNING,
                    "Failed to renew heartbeat leader lease",
                    it,
                )
              }
              .getOrElse { false }
        } else {
          false
        }
    if (stillLeader) {
      leader.set(true)
      return
    }
    val acquired =
        runCatching { tryAcquire() }
            .onFailure {
              log.log(
                  System.Logger.Level.WARNING,
                  "Failed to acquire heartbeat leader lease",
                  it,
              )
            }
            .getOrElse { false }
    leader.set(acquired)
  }
}
