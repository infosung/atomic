package com.infosung.atomic.contract.workflow

import kotlin.test.Test
import kotlin.test.assertTrue

class TagAndReleaseWorkflowContractTest {
  private companion object {
    val workflow by lazy {
      WorkflowContractFixtures.readWorkflow(".github/workflows/tag-and-release.yml")
    }
  }

  @Test
  fun `tag and release workflow should keep semver and duplicate tag guards`() {
    assertTrue(workflow.contains("workflow_dispatch:"), "manual trigger must remain")
    assertTrue(
        workflow.contains("workflow_dispatch must run on refs/heads/main"),
        "manual main branch guard must remain",
    )
    assertTrue(
        workflow.contains("projectVersion must be SemVer x.y.z for release tagging"),
        "release tagging should keep the semver guard",
    )
    assertTrue(
        workflow.contains("projectVersion unchanged"),
        "unchanged projectVersion should skip retagging",
    )
    assertTrue(
        workflow.contains("Tag already exists: v"),
        "existing tags should skip duplicate tagging",
    )
  }

  @Test
  fun `tag and release workflow should keep tag push and release creation flow`() {
    assertTrue(workflow.contains("git tag"), "release workflow should create the git tag")
    assertTrue(workflow.contains("git push origin"), "release workflow should push the release tag")
    assertTrue(
        workflow.contains("docs/release-notes/\${{ steps.resolve.outputs.version }}.md"),
        "release workflow should require a versioned release notes file",
    )
    assertTrue(
        workflow.contains("gh release create"),
        "release workflow should create a GitHub release from the tag",
    )
    assertTrue(
        workflow.contains("--notes-file"),
        "release workflow should publish the curated release notes file instead of generated notes",
    )
  }
}
