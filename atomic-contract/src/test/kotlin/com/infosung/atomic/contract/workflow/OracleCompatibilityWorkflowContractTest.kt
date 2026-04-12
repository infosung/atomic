package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertTrue

class OracleCompatibilityWorkflowContractTest {
  @Test
  fun `oracle compatibility workflow should exist and run focused oracle verification`() {
    val workflow =
        WorkflowContractFixtures.readWorkflow(".github/workflows/oracle-compatibility.yml")

    assertTrue(workflow.contains("name: Oracle Compatibility"))
    assertTrue(workflow.contains("pull_request:"))
    assertTrue(workflow.contains("workflow_dispatch:"))
    assertTrue(workflow.contains("Setup Java"))
    assertTrue(workflow.contains("./gradlew --no-configuration-cache"))
    assertTrue(workflow.contains(":atomic-contract:test --tests"))
    assertTrue(workflow.contains("JdbcTableMetadataLoaderVendorCompatibilityTest"))
    assertTrue(workflow.contains("AppVersionOracleCompatibilityContractTest"))
    assertTrue(workflow.contains("AppImageOracleCompatibilityContractTest"))
    assertTrue(workflow.contains("JpaOauthRelayCodeStoreOracleCompatibilityTest"))
  }
}
