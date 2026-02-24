package com.infosung.atomic.contract.header

import java.util.UUID

class TraceIdGenerator(
    private val defaultSupplier: () -> String = { UUID.randomUUID().toString() },
) {
  @Volatile private var supplier: () -> String = defaultSupplier

  fun generate(): String = supplier.invoke()

  fun configure(supplier: () -> String) {
    this.supplier = supplier
  }

  fun reset() {
    this.supplier = defaultSupplier
  }
}
