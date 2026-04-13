package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowActionRuntimeContractTest {
  @Test
  fun `github workflows should stay on node24 compatible action majors`() {
    val workflowPaths =
        listOf(
            ".github/workflows/ci.yml",
            ".github/workflows/oracle-compatibility.yml",
            ".github/workflows/publish-maven-central.yml",
            ".github/workflows/tag-and-release.yml",
        )

    workflowPaths.forEach { path ->
      val workflow = WorkflowContractFixtures.readWorkflow(path)
      assertFalse(
          workflow.contains("actions/checkout@v4"),
          "$path should not keep checkout on the deprecated Node 20 action major",
      )
      assertFalse(
          workflow.contains("actions/setup-java@v4"),
          "$path should not keep setup-java on the deprecated Node 20 action major",
      )
      assertFalse(
          workflow.contains("gradle/actions/setup-gradle@v4"),
          "$path should not keep setup-gradle on the deprecated Node 20 action major",
      )
    }

    val ciWorkflow = WorkflowContractFixtures.readWorkflow(".github/workflows/ci.yml")
    val oracleWorkflow =
        WorkflowContractFixtures.readWorkflow(".github/workflows/oracle-compatibility.yml")
    val publishWorkflow =
        WorkflowContractFixtures.readWorkflow(".github/workflows/publish-maven-central.yml")
    val releaseWorkflow =
        WorkflowContractFixtures.readWorkflow(".github/workflows/tag-and-release.yml")

    assertTrue(
        ciWorkflow.contains("actions/checkout@v5"),
        "ci workflow should use checkout v5",
    )
    assertTrue(
        ciWorkflow.contains("actions/setup-java@v5"),
        "ci workflow should use setup-java v5",
    )
    assertTrue(
        ciWorkflow.contains("gradle/actions/setup-gradle@v5"),
        "ci workflow should use setup-gradle v5",
    )
    assertTrue(
        oracleWorkflow.contains("actions/checkout@v5"),
        "oracle compatibility workflow should use checkout v5",
    )
    assertTrue(
        oracleWorkflow.contains("actions/setup-java@v5"),
        "oracle compatibility workflow should use setup-java v5",
    )
    assertTrue(
        oracleWorkflow.contains("gradle/actions/setup-gradle@v5"),
        "oracle compatibility workflow should use setup-gradle v5",
    )
    assertTrue(
        publishWorkflow.contains("actions/checkout@v5"),
        "publish workflow should use checkout v5",
    )
    assertTrue(
        publishWorkflow.contains("actions/setup-java@v5"),
        "publish workflow should use setup-java v5",
    )
    assertTrue(
        publishWorkflow.contains("gradle/actions/setup-gradle@v5"),
        "publish workflow should use setup-gradle v5",
    )
    assertTrue(
        releaseWorkflow.contains("actions/checkout@v5"),
        "tag and release workflow should use checkout v5",
    )
  }
}
