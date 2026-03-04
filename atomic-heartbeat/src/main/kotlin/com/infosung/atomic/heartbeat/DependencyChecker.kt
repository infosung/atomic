package com.infosung.atomic.heartbeat

/** Performs one dependency health probe. */
fun interface DependencyChecker {
  fun check(): DependencyCheckResult
}
