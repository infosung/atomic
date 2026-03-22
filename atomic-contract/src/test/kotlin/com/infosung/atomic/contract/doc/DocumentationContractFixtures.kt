package com.infosung.atomic.contract.doc

import java.nio.file.Files
import java.nio.file.Path

object DocumentationContractFixtures {
  fun read(path: String): String = Files.readString(findRepoRoot().resolve(path))

  private fun findRepoRoot(): Path {
    val startPath = Path.of("").toAbsolutePath()
    return generateSequence(startPath) { it.parent }
        .find {
          Files.exists(it.resolve("docs/usage")) && Files.exists(it.resolve("settings.gradle.kts"))
        } ?: error("Failed to locate repository root from $startPath")
  }
}
