package com.infosung.atomic.contract.workflow

import java.nio.file.Files
import java.nio.file.Path

object WorkflowContractFixtures {
  fun readWorkflow(path: String): String = Files.readString(findRepoRoot().resolve(path))

  private fun findRepoRoot(): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
      if (Files.exists(current.resolve(".github/workflows"))) {
        return current
      }
      current.parent?.let { current = it }
          ?: error("Failed to locate repository root from ${Path.of("").toAbsolutePath()}")
    }
  }
}
