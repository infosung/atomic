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
    val actualModules =
        WorkflowContractFixtures.readPublishModules(".github/workflows/publish-maven-central.yml")

    assertTrue(
        actualModules == expectedModules,
        "publish workflow should keep module list stable: expected=$expectedModules actual=$actualModules",
    )
  }

  @Test
  fun `publish workflow should keep release context guards for reusable flow and manual rerun`() {
    assertTrue(workflow.contains("workflow_call:"), "workflow_call trigger must remain")
    assertTrue(workflow.contains("workflow_dispatch:"), "workflow_dispatch trigger must remain")
    assertFalse(
        workflow.lineSequence().any { it.trim().startsWith("workflow_run:") },
        "publish workflow should no longer depend on workflow_run default-branch context",
    )
    assertTrue(
        workflow.contains("release_tag:"),
        "publish workflow should require an explicit release_tag input",
    )
    assertTrue(
        workflow.contains("release_tag input must be SemVer"),
        "publish workflow should validate the explicit release tag format",
    )
    assertTrue(
        workflow.contains("GitHub release does not exist"),
        "publish workflow should require the GitHub release to exist before publishing",
    )
    assertTrue(
        workflow.contains("Release version does not match requested tag"),
        "publish workflow should verify that the checked out project version matches the requested tag",
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
        "publish workflow should keep tag checkout verification",
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

  @Test
  fun `publish workflow verify job should include oracle focused release gate verification`() {
    assertTrue(
        workflow.contains("Verify Oracle compatibility release gate"),
        "publish workflow should keep an explicit Oracle-focused release gate step",
    )
    assertTrue(
        workflow.contains("JdbcTableMetadataLoaderVendorCompatibilityTest"),
        "publish workflow should verify JDBC metadata compatibility in the release gate",
    )
    assertTrue(
        workflow.contains("AppVersionOracleCompatibilityContractTest"),
        "publish workflow should verify app-version Oracle compatibility in the release gate",
    )
    assertTrue(
        workflow.contains("AppImageOracleCompatibilityContractTest"),
        "publish workflow should verify storage-api Oracle compatibility in the release gate",
    )
    assertTrue(
        workflow.contains("JpaOauthRelayCodeStoreOracleCompatibilityTest"),
        "publish workflow should verify oauth-redirect Oracle compatibility in the release gate",
    )
  }
}
