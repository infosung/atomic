package com.infosung.atomic.contract.workflow

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishMavenCentralWorkflowContractTest {
  @Test
  fun `publish workflow should publish modules sequentially instead of matrix fanout`() {
    val workflow = publishWorkflow()

    assertFalse(workflow.contains("matrix:"), "publish workflow should not use matrix fanout")
    assertFalse(
        workflow.contains("\${{ matrix.module }}"),
        "publish workflow should not depend on matrix module expansion",
    )
    assertTrue(
        workflow.contains("MODULES=("),
        "publish workflow should define a sequential module list",
    )
    assertTrue(
        workflow.contains("for MODULE in \"\${MODULES[@]}\"; do"),
        "publish workflow should publish modules in a shell loop",
    )
  }

  @Test
  fun `publish workflow should keep release context guards for workflow run and manual rerun`() {
    val workflow = publishWorkflow()

    assertTrue(workflow.contains("workflow_run:"), "workflow_run trigger must remain")
    assertTrue(workflow.contains("workflow_dispatch:"), "workflow_dispatch trigger must remain")
    assertTrue(
        workflow.contains("github.event.workflow_run.head_sha"),
        "workflow_run source SHA guard must remain",
    )
    assertTrue(
        workflow.contains("workflow_dispatch must run on refs/heads/main"),
        "manual main branch guard must remain",
    )
    assertTrue(
        workflow.contains("Manual publish requires HEAD to match"),
        "manual publish should verify that current HEAD matches the release tag commit",
    )
  }

  @Test
  fun `publish workflow should serialize publish execution per release tag`() {
    val workflow = publishWorkflow()

    assertTrue(workflow.contains("concurrency:"), "publish workflow should define concurrency")
    assertTrue(
        workflow.contains("maven-central-"),
        "publish workflow should serialize publish jobs per release tag",
    )
  }

  private fun publishWorkflow(): String {
    val workflowPath = findRepoRoot().resolve(".github/workflows/publish-maven-central.yml")
    return Files.readString(workflowPath)
  }

  private fun findRepoRoot(): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
      if (Files.exists(current.resolve(".github/workflows/publish-maven-central.yml"))) {
        return current
      }
      current.parent?.let { current = it }
          ?: error("Failed to locate repository root from ${Path.of("").toAbsolutePath()}")
    }
  }
}
