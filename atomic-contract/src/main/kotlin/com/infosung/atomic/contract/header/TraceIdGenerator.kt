package com.infosung.atomic.contract.header

import java.util.UUID

/** Thread-safe trace-id generator with pluggable supplier. */
class TraceIdGenerator(
    private val defaultSupplier: () -> String = { UUID.randomUUID().toString() },
) {
  @Volatile private var supplier: () -> String = defaultSupplier

  /** Generates a new trace id using current supplier. */
  fun generate(): String = supplier.invoke()

  /** Overrides the id supplier (useful for tests or custom formats). */
  fun configure(supplier: () -> String) {
    this.supplier = supplier
  }

  /** Restores the default UUID-based supplier. */
  fun reset() {
    this.supplier = defaultSupplier
  }
}
