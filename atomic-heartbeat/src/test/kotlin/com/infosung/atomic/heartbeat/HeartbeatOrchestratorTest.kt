package com.infosung.atomic.heartbeat

import java.time.Duration
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartbeatOrchestratorTest {
  @Test
  fun `healthy required check should emit success ping`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "db",
                        checker = DependencyChecker { DependencyCheckResult(healthy = true) },
                        required = true,
                        interval = Duration.ofMillis(30),
                        timeout = Duration.ofMillis(100),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) { events.any { it.type == HeartbeatEventType.SUCCESS } }
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `unhealthy required check should emit fail ping`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "redis",
                        checker =
                            DependencyChecker {
                              DependencyCheckResult(healthy = false, message = "PING failed")
                            },
                        required = true,
                        interval = Duration.ofMillis(30),
                        timeout = Duration.ofMillis(100),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) { events.any { it.type == HeartbeatEventType.FAIL } }
      assertTrue(events.any { it.message?.contains("redis") == true })
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `leader dedup should skip ping when instance is not leader`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val elector =
        object : LeaderElector {
          override fun start() {}

          override fun stop() {}

          override fun isLeader(): Boolean = false
        }
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = true,
            pingFailOpen = false,
            dedupMode = DedupMode.LEADER,
            leaderElector = elector,
            checkPlans = emptyList(),
        )

    orchestrator.start()
    try {
      Thread.sleep(400)
      assertEquals(0, events.size)
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `per-instance dedup should emit ping`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.PER_INSTANCE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "db",
                        checker = DependencyChecker { DependencyCheckResult(healthy = true) },
                        required = true,
                        interval = Duration.ofMillis(120),
                        timeout = Duration.ofMillis(100),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) { events.any { it.type == HeartbeatEventType.SUCCESS } }
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `ping and check intervals should be independently scheduled`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    var checkCount = 0
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 30,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "db",
                        checker =
                            DependencyChecker {
                              checkCount += 1
                              DependencyCheckResult(healthy = true)
                            },
                        required = true,
                        interval = Duration.ofMillis(200),
                        timeout = Duration.ofMillis(100),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) { events.count { it.type == HeartbeatEventType.SUCCESS } >= 3 }
      assertTrue(checkCount in 1..5, "checkCount=$checkCount")
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `timeout in dependency check should emit fail ping`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "db",
                        checker =
                            DependencyChecker {
                              Thread.sleep(200)
                              DependencyCheckResult(healthy = true)
                            },
                        required = true,
                        interval = Duration.ofMillis(30),
                        timeout = Duration.ofMillis(20),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) { events.any { it.type == HeartbeatEventType.FAIL } }
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `dependency checker exception should expose root cause class in fail message`() {
    val events = Collections.synchronizedList(mutableListOf<HeartbeatEvent>())
    val orchestrator =
        HeartbeatOrchestrator(
            provider = HeartbeatProvider { events.add(it) },
            pingIntervalMillis = 50,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans =
                listOf(
                    DependencyCheckPlan(
                        id = "db",
                        checker = DependencyChecker { throw IllegalArgumentException("boom") },
                        required = true,
                        interval = Duration.ofMillis(30),
                        timeout = Duration.ofMillis(100),
                    )),
        )

    orchestrator.start()
    try {
      eventually(1_500) {
        events.any {
          it.type == HeartbeatEventType.FAIL &&
              it.message?.contains("IllegalArgumentException") == true
        }
      }
      val failMessage = events.lastOrNull { it.type == HeartbeatEventType.FAIL }?.message ?: ""
      assertTrue(failMessage.contains("IllegalArgumentException"), failMessage)
      assertTrue(!failMessage.contains("ExecutionException"), failMessage)
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `provider exception should not stop future ping attempts when failOpen is false`() {
    val attempts = AtomicInteger(0)
    val orchestrator =
        HeartbeatOrchestrator(
            provider =
                HeartbeatProvider {
                  val attempt = attempts.incrementAndGet()
                  if (attempt <= 2) {
                    throw IllegalStateException("transient network failure")
                  }
                },
            pingIntervalMillis = 40,
            sendStartEvent = false,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans = emptyList(),
        )

    orchestrator.start()
    try {
      eventually(2_000) { attempts.get() >= 3 }
      assertTrue(orchestrator.pingSendFailureCount() >= 1)
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `provider exception with failOpen true should not increase strict failure counter`() {
    val attempts = AtomicInteger(0)
    val orchestrator =
        HeartbeatOrchestrator(
            provider =
                HeartbeatProvider {
                  attempts.incrementAndGet()
                  throw IllegalStateException("transport unavailable")
                },
            pingIntervalMillis = 40,
            sendStartEvent = false,
            pingFailOpen = true,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans = emptyList(),
        )

    orchestrator.start()
    try {
      eventually(2_000) { attempts.get() >= 3 }
      assertEquals(0, orchestrator.pingSendFailureCount())
    } finally {
      orchestrator.stop()
    }
  }

  @Test
  fun `start event failure should not prevent orchestrator startup`() {
    val attempts = AtomicInteger(0)
    val orchestrator =
        HeartbeatOrchestrator(
            provider =
                HeartbeatProvider {
                  val attempt = attempts.incrementAndGet()
                  if (attempt == 1) {
                    throw IllegalStateException("start ping failed")
                  }
                },
            pingIntervalMillis = 40,
            sendStartEvent = true,
            pingFailOpen = false,
            dedupMode = DedupMode.NONE,
            leaderElector = NoopLeaderElector(),
            checkPlans = emptyList(),
        )

    orchestrator.start()
    try {
      eventually(2_000) { attempts.get() >= 2 }
      assertTrue(orchestrator.pingSendFailureCount() >= 1)
    } finally {
      orchestrator.stop()
    }
  }

  private fun eventually(timeoutMillis: Long, assertion: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMillis) {
      if (assertion()) return
      Thread.sleep(25)
    }
    assertTrue(assertion())
  }
}
