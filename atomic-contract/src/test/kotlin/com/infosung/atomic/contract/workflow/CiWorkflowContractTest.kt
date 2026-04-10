package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertTrue

class CiWorkflowContractTest {
  private companion object {
    val workflow by lazy { WorkflowContractFixtures.readWorkflow(".github/workflows/ci.yml") }
  }

  @Test
  fun `ci workflow should keep existing compatibility lanes and add previous latest stable lanes`() {
    assertTrue(
        workflow.contains("springboot: \"4.0.3\""),
        "ci workflow should keep the baseline Spring Boot lane",
    )
    assertTrue(
        workflow.contains("springboot: \"4.0.2\""),
        "ci workflow should keep the first Spring Boot compatibility lane",
    )
    assertTrue(
        workflow.contains("springboot: \"4.0.1\""),
        "ci workflow should keep the second Spring Boot compatibility lane",
    )
    assertTrue(
        workflow.contains("kotlin: \"2.3.0\""),
        "ci workflow should keep the Kotlin compatibility lane",
    )
    assertTrue(
        workflow.contains("springboot: \"4.0.4\""),
        "ci workflow should include the previous stable Spring Boot patch lane",
    )
    assertTrue(
        workflow.contains("springboot: \"4.0.5\""),
        "ci workflow should keep the latest stable Spring Boot lane",
    )
    assertTrue(
        workflow.contains("kotlin: \"2.3.10\""),
        "ci workflow should keep the current catalog Kotlin lane",
    )
    assertTrue(
        workflow.contains("kotlin: \"2.3.20\""),
        "ci workflow should keep the latest stable Kotlin lane",
    )
    assertTrue(
        workflow.contains("latest-stable"),
        "ci workflow should include a latest stable verification lane",
    )
  }

  @Test
  fun `ci workflow should keep explicit static matrix instead of dynamic version resolution`() {
    assertTrue(
        workflow.contains("baseline"),
        "ci workflow should keep the baseline verification lane",
    )
    assertTrue(
        workflow.contains("compat"),
        "ci workflow should keep the compatibility verification lanes",
    )
    assertTrue(
        workflow.contains("previous-stable-boot"),
        "ci workflow should verify the previous Spring Boot patch release",
    )
    assertTrue(
        workflow.contains("latest-boot"),
        "ci workflow should verify the latest Spring Boot against the catalog Kotlin version",
    )
    assertTrue(
        workflow.contains("latest-kotlin"),
        "ci workflow should verify the latest Kotlin against the catalog Spring Boot version",
    )
    assertTrue(
        !workflow.contains("resolve-verify-matrix:"),
        "ci workflow should not use a separate dynamic matrix resolution job",
    )
    assertTrue(
        !workflow.contains("repo.maven.apache.org"),
        "ci workflow should not resolve versions dynamically from Maven metadata",
    )
    assertTrue(
        !workflow.contains("fromJson("),
        "ci workflow should keep an explicit static matrix",
    )
  }
}
