package com.infosung.atomic.heartbeat

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaseLeaderElectorTest {
  @Test
  fun `second elector should acquire leadership after first elector stops`() {
    val owner = AtomicReference<String?>(null)
    val leaseUntil = AtomicLong(0L)
    val leaseMillis = 160L

    fun newElector(id: String): LeaseLeaderElector {
      fun now(): Long = System.currentTimeMillis()
      return LeaseLeaderElector(
          renewInterval = Duration.ofMillis(40),
          schedulerThreadName = "lease-elector-$id",
          tryAcquire = {
            synchronized(owner) {
              val currentOwner = owner.get()
              val expired = leaseUntil.get() <= now()
              if (currentOwner == null || expired) {
                owner.set(id)
                leaseUntil.set(now() + leaseMillis)
                true
              } else {
                false
              }
            }
          },
          tryRenew = {
            synchronized(owner) {
              if (owner.get() != id) return@synchronized false
              leaseUntil.set(now() + leaseMillis)
              true
            }
          },
          tryRelease = {
            synchronized(owner) {
              if (owner.get() == id) {
                owner.set(null)
                leaseUntil.set(0L)
              }
            }
          },
      )
    }

    val elector1 = newElector("node-1")
    val elector2 = newElector("node-2")
    elector1.start()
    elector2.start()
    try {
      eventually(1_500) { elector1.isLeader() }
      assertFalse(elector2.isLeader())

      elector1.stop()

      eventually(2_500) { elector2.isLeader() }
      assertTrue(elector2.isLeader())
    } finally {
      elector1.stop()
      elector2.stop()
    }
  }

  @Test
  fun `stop should tolerate release failure and clear leadership`() {
    val elector =
        LeaseLeaderElector(
            renewInterval = Duration.ofMillis(40),
            schedulerThreadName = "lease-elector-release-failure",
            tryAcquire = { true },
            tryRenew = { true },
            tryRelease = { throw IllegalStateException("release failed") },
        )

    elector.start()
    try {
      eventually(1_500) { elector.isLeader() }
    } finally {
      elector.stop()
    }

    assertFalse(elector.isLeader())
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
