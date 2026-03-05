package com.infosung.atomic.heartbeat

/** Sends heartbeat signals to an external monitoring provider. */
fun interface HeartbeatProvider {
  fun send(event: HeartbeatEvent)
}
