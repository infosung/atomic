package com.infosung.atomic.heartbeat

/** Controls leader election for deduplicated heartbeat emission. */
interface LeaderElector {
  fun start()

  fun stop()

  fun isLeader(): Boolean
}

/** No-op elector used for non-leader dedup modes. */
class NoopLeaderElector : LeaderElector {
  override fun start() {}

  override fun stop() {}

  override fun isLeader(): Boolean = true
}
