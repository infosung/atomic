package com.infosung.atomic.spring.web.log

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle

/**
 * In-memory buffered service logger that flushes logs in batches via [LogSaver].
 */
class ServiceLogger(
    private val logSaver: LogSaver,
    private val maxQueueSize: Int = 10_000,
) : SmartLifecycle {
  private val log: Logger = LoggerFactory.getLogger(ServiceLogger::class.java)

  private val isSending = AtomicBoolean(false)
  private var running = false
  private val queue: Queue<ServiceLog> = ConcurrentLinkedQueue()
  private val tempQueue: Queue<ServiceLog> = LinkedList()
  private val queueSize = AtomicInteger(0)
  private val droppedLogCount = AtomicLong(0)

  init {
    require(maxQueueSize > 0) { "maxQueueSize must be greater than 0." }
  }

  /**
   * Adds one log event into queue.
   *
   * When queue is full, oldest entries are dropped.
   */
  fun logging(data: ServiceLog?) {
    log.trace("Queueing service log payload: {}", data)
    if (data == null) {
      log.debug("Skipping null service log payload")
      return
    }
    synchronized(queue) {
      if (queueSize.get() >= maxQueueSize) {
        if (queue.poll() != null) {
          queueSize.decrementAndGet()
          val dropped = droppedLogCount.incrementAndGet()
          if (dropped == 1L || dropped % 1_000L == 0L) {
            log.warn("Log queue is full. Dropped oldest log(s): dropped={}", dropped)
          }
        }
      }
      queue.add(data)
      queueSize.incrementAndGet()
    }
  }

  /**
   * Flushes current queue to [LogSaver].
   *
   * This method is re-entrancy safe.
   */
  fun send() {
    if (!isSending.compareAndSet(false, true)) return
    if (queueSize.get() == 0 && tempQueue.isEmpty()) {
      isSending.set(false)
      return
    }
    try {
      copyQueue()
      sendLogs()
    } catch (e: Exception) {
      log.error("Failed to persist logs", e)
    } finally {
      isSending.set(false)
    }
  }

  private fun copyQueue() {
    log.debug("Moving logs to temporary queue: count={}", queueSize.get())
    synchronized(queue) {
      while (true) {
        val data = queue.poll() ?: break
        queueSize.decrementAndGet()
        tempQueue.add(data)
      }
    }
  }

  private fun sendLogs() {
    val sendingCount = tempQueue.size
    log.debug("Sending logs: count={}", sendingCount)
    try {
      logSaver.saveAll(tempQueue.toList())
    } catch (e: Exception) {
      log.error("Failed to persist logs", e)
      return
    }
    log.info("Logs persisted successfully: count={}", sendingCount)
    tempQueue.clear()
  }

  override fun start() {
    running = true
  }

  override fun stop() {
    log.info("Flushing all pending logs before shutdown")
    this.send()
    running = false
  }

  override fun isRunning(): Boolean = running

  override fun getPhase(): Int = 0
}
