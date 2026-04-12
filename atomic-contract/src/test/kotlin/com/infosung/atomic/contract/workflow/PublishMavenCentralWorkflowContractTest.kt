package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublishMavenCentralWorkflowContractTest {
  private companion object {
    val workflow by lazy {
      WorkflowContractFixtures.readWorkflow(".github/workflows/publish-maven-central.yml")
    }
  }

  @Test
  fun `publish workflow should publish modules sequentially instead of matrix fanout`() {
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
  fun `publish workflow should keep exact module list in release order`() {
    val expectedModules =
        listOf(
            "atomic-event-log",
            "atomic-event-log:iceberg",
            "atomic-event-log:parquet",
            "atomic-event-log:duckdb",
            "atomic-event-log:spring-web",
            "atomic-event-log:ingest-api",
            "atomic-contract",
            "atomic-storage",
            "atomic-spring-web",
            "atomic-spring-security",
            "atomic-spring-idempotency",
            "atomic-spring-oauth2",
            "atomic-heartbeat",
            "atomic-starter",
            "atomic-app:app-version",
            "atomic-app:oauth-redirect",
            "atomic-app:storage-api",
            "atomic-app",
        )
    val modulesBlock = workflow.substringAfter("MODULES=(").substringBefore(")")
    val actualModules =
        modulesBlock.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

    assertTrue(
        actualModules == expectedModules,
        "publish workflow should keep module list stable: expected=$expectedModules actual=$actualModules",
    )
  }

  @Test
  fun `publish workflow should keep release context guards for workflow run and manual rerun`() {
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
    assertTrue(
        workflow.contains("SNAPSHOT version cannot be published to Maven Central"),
        "publish workflow should reject snapshot versions",
    )
    assertTrue(
        workflow.contains("Release tag does not exist on origin"),
        "publish workflow should require the release tag to exist on origin",
    )
    assertTrue(
        workflow.contains("Tag commit mismatch"),
        "publish workflow should keep tag to source SHA verification",
    )
    assertTrue(
        workflow.contains("is not contained in origin/main"),
        "publish workflow should ensure the tagged commit is contained in origin/main",
    )
  }

  @Test
  fun `publish workflow should serialize publish execution per release tag`() {
    assertTrue(workflow.contains("concurrency:"), "publish workflow should define concurrency")
    assertTrue(
        workflow.contains("maven-central-"),
        "publish workflow should serialize publish jobs per release tag",
    )
  }
}
