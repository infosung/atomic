package com.infosung.atomic.event.log.ingest.api.adapter.out.worker

import com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle

/** Spring lifecycle adapter that continuously drains async ingest queue entries. */
class EventLogAsyncIngestWorkerLifecycle(
    private val processService: ProcessQueuedEventLogBatchesService,
    private val pollDelay: Duration,
    private val pollLimit: Int,
    private val shutdownDrainTimeout: Duration,
) : SmartLifecycle {
  private val log = LoggerFactory.getLogger(this::class.java)
  @Volatile private var executor: ScheduledExecutorService? = null
  @Volatile private var running = false

  override fun start() {
    if (running) {
      return
    }
    val laneIds = processService.laneIds()
    running = true
    executor =
        Executors.newScheduledThreadPool(laneIds.size.coerceAtLeast(1)) { runnable ->
          Thread(runnable, "atomic-event-log-async-worker").apply { isDaemon = true }
        }
    laneIds.forEach { laneId ->
      executor!!.scheduleWithFixedDelay(
          {
            try {
              processService.drainLaneOnce(laneId = laneId, limit = pollLimit)
            } catch (e: Exception) {
              log.error("Async event-log worker iteration failed: laneId={}", laneId, e)
            }
          },
          0,
          pollDelay.toMillis(),
          TimeUnit.MILLISECONDS,
      )
    }
    log.debug(
        "Async event-log worker started: laneCount={}, pollDelayMs={}, pollLimit={}",
        laneIds.size,
        pollDelay.toMillis(),
        pollLimit,
    )
  }

  override fun stop() {
    if (!running) {
      return
    }
    running = false
    processService.beginShutdown()
    val currentExecutor = executor
    currentExecutor?.shutdown()
    val terminated =
        currentExecutor?.awaitTermination(
            shutdownDrainTimeout.toMillis().coerceAtLeast(1L), TimeUnit.MILLISECONDS) ?: true
    if (!terminated) {
      log.warn(
          "Async event-log worker did not terminate within shutdown timeout. Requesting interruption before inline drain: timeoutMs={}",
          shutdownDrainTimeout.toMillis(),
      )
      currentExecutor.shutdownNow()
      val interruptedTermination =
          currentExecutor.awaitTermination(
              pollDelay.toMillis().coerceAtLeast(1L), TimeUnit.MILLISECONDS)
      if (!interruptedTermination) {
        log.warn(
            "Async event-log worker still running after forced shutdown. Skipping inline drain to avoid duplicate processing.",
        )
        logRemainingQueue()
        executor = null
        return
      }
    }
    drainRemainingQueue()
    executor = null
    log.debug("Async event-log worker stopped.")
  }

  override fun stop(callback: Runnable) {
    stop()
    callback.run()
  }

  override fun isRunning(): Boolean = running

  override fun isAutoStartup(): Boolean = true

  private fun drainRemainingQueue() {
    val deadlineNanos = System.nanoTime() + shutdownDrainTimeout.toNanos()
    while (System.nanoTime() < deadlineNanos) {
      var hasPending = false
      var madeProgress = false
      processService.laneIds().forEach { laneId ->
        val checkpoint = processService.checkpoint(laneId)
        if (checkpoint.queuedRequestCount <= 0) {
          return@forEach
        }
        hasPending = true
        val result = processService.drainLaneOnce(laneId = laneId, limit = pollLimit)
        if (result.processed > 0 || result.validationRejected > 0) {
          madeProgress = true
        }
      }
      if (!hasPending) {
        log.debug("Async event-log worker drained remaining queue during shutdown.")
        return
      }
      if (!madeProgress) {
        try {
          Thread.sleep(pollDelay.toMillis().coerceAtLeast(1L))
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }
      }
    }
    logRemainingQueue()
  }

  private fun logRemainingQueue() {
    val remaining =
        processService
            .laneIds()
            .associateWith { processService.checkpoint(it).queuedRequestCount }
            .filterValues { it > 0 }
    if (remaining.isNotEmpty()) {
      log.warn(
          "Async event-log worker stopped with remaining queued batches after shutdown drain timeout: remaining={}",
          remaining,
      )
    }
  }
}
