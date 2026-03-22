package com.infosung.atomic.contract.doc

import java.nio.file.Files
import java.nio.file.Path

object DocumentationContractFixtures {
  fun read(path: String): String = Files.readString(findRepoRoot().resolve(path))

  private fun findRepoRoot(): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
      if (Files.exists(current.resolve("docs/usage")) &&
          Files.exists(current.resolve("settings.gradle.kts"))) {
        return current
      }
      current.parent?.let { current = it }
          ?: error("Failed to locate repository root from ${Path.of("").toAbsolutePath()}")
    }
  }
}
