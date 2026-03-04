package com.infosung.atomic.heartbeat

import com.infosung.atomic.contract.time.TimeProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Runs dependency checks and sends periodic heartbeat pings. */
class HeartbeatOrchestrator(
    private val provider: HeartbeatProvider,
    private val pingIntervalMillis: Long,
    private val sendStartEvent: Boolean,
    private val pingFailOpen: Boolean,
    private val dedupMode: DedupMode,
    private val leaderElector: LeaderElector,
    private val checkPlans: List<DependencyCheckPlan>,
    private val timeProvider: TimeProvider = TimeProvider(),
    private val schedulerThreadPrefix: String = "atomic-heartbeat",
) {
  private val started = AtomicBoolean(false)
  private val checkStates = ConcurrentHashMap<String, CheckState>()
  private val pingSendFailureCount = AtomicLong(0)

  @Volatile private var scheduler: ScheduledExecutorService? = null
  @Volatile private var checkExecutor: ExecutorService? = null
  @Volatile private var pingFuture: ScheduledFuture<*>? = null
  @Volatile private var checkFutures: MutableList<ScheduledFuture<*>> = mutableListOf()

  fun start() {
    if (!started.compareAndSet(false, true)) return

    checkExecutor =
        Executors.newFixedThreadPool(maxOf(1, checkPlans.size)) { runnable ->
          Thread(runnable, "$schedulerThreadPrefix-check").apply { isDaemon = true }
        }

    val poolSize = maxOf(1, checkPlans.size + 1)
    val newScheduler =
        Executors.newScheduledThreadPool(poolSize) { runnable ->
          Thread(runnable, "$schedulerThreadPrefix-loop").apply { isDaemon = true }
        }
    scheduler = newScheduler

    if (dedupMode == DedupMode.LEADER) {
      leaderElector.start()
    }

    checkFutures =
        checkPlans
            .map { plan ->
              newScheduler.scheduleWithFixedDelay(
                  { runCheck(plan) },
                  0L,
                  plan.interval.toMillis(),
                  TimeUnit.MILLISECONDS,
              )
            }
            .toMutableList()

    if (sendStartEvent && shouldEmitPing()) {
      runCatching { sendEvent(HeartbeatEvent(type = HeartbeatEventType.START)) }
          .onFailure {
            // Startup should not fail only because heartbeat transport is temporarily unavailable.
            if (!pingFailOpen) {
              pingSendFailureCount.incrementAndGet()
            }
          }
    }

    pingFuture =
        newScheduler.scheduleWithFixedDelay(
            { emitPingSafely() },
            pingIntervalMillis,
            pingIntervalMillis,
            TimeUnit.MILLISECONDS,
        )
  }

  fun stop() {
    if (!started.compareAndSet(true, false)) return

    pingFuture?.cancel(true)
    pingFuture = null
    checkFutures.forEach { it.cancel(true) }
    checkFutures = mutableListOf()
    scheduler?.shutdownNow()
    scheduler = null

    checkExecutor?.shutdownNow()
    checkExecutor = null

    if (dedupMode == DedupMode.LEADER) {
      leaderElector.stop()
    }
  }

  private fun runCheck(plan: DependencyCheckPlan) {
    val checkTask = checkExecutor ?: return
    val now = timeProvider.nowMillis()
    val checkResult = runCheckWithTimeout(checkTask, plan)
    checkStates[plan.id] = CheckState(result = checkResult, checkedAtMillis = now)
  }

  private fun runCheckWithTimeout(
      checkTask: ExecutorService,
      plan: DependencyCheckPlan,
  ): DependencyCheckResult {
    val future: Future<DependencyCheckResult> =
        checkTask.submit<DependencyCheckResult> { plan.checker.check() }
    return try {
      future.get(plan.timeout.toMillis(), TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
      future.cancel(true)
      DependencyCheckResult(
          healthy = false,
          message = "Dependency check timed out: ${plan.id}",
      )
    } catch (e: Exception) {
      future.cancel(true)
      DependencyCheckResult(
          healthy = false,
          message = "Dependency check failed: ${plan.id} (${e.javaClass.simpleName})",
      )
    }
  }

  private fun emitPing() {
    if (!shouldEmitPing()) return

    val failures = collectFailures()
    val event =
        if (failures.isEmpty()) {
          HeartbeatEvent(type = HeartbeatEventType.SUCCESS)
        } else {
          HeartbeatEvent(
              type = HeartbeatEventType.FAIL,
              message = failures.joinToString("; "),
          )
        }
    sendEvent(event)
  }

  private fun emitPingSafely() {
    runCatching { emitPing() }
        .onFailure {
          // Keep periodic scheduling alive even when ping emission fails.
          if (!pingFailOpen) {
            pingSendFailureCount.incrementAndGet()
          }
        }
  }

  private fun shouldEmitPing(): Boolean {
    if (dedupMode != DedupMode.LEADER) return true
    return leaderElector.isLeader()
  }

  private fun collectFailures(): List<String> {
    val now = timeProvider.nowMillis()
    val failures = mutableListOf<String>()
    for (plan in checkPlans) {
      if (!plan.required) continue
      val state = checkStates[plan.id]
      if (state == null) {
        failures.add("${plan.id}: no check result")
        continue
      }
      val stale = now - state.checkedAtMillis > plan.staleAfter.toMillis()
      if (stale) {
        failures.add("${plan.id}: stale result")
        continue
      }
      if (!state.result.healthy) {
        val reason = state.result.message ?: "unhealthy"
        failures.add("${plan.id}: $reason")
      }
    }
    return failures
  }

  private fun sendEvent(event: HeartbeatEvent) {
    if (pingFailOpen) {
      runCatching { provider.send(event) }.onFailure {}
      return
    }
    provider.send(event)
  }

  internal fun pingSendFailureCount(): Long = pingSendFailureCount.get()

  private data class CheckState(
      val result: DependencyCheckResult,
      val checkedAtMillis: Long,
  )
}
