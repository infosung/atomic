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
        workflow.contains("release_tag:"),
        "manual release workflow should support an explicit recovery tag input",
    )
    assertTrue(
        workflow.contains("release_tag input must be SemVer"),
        "manual recovery tag input should be validated as SemVer",
    )
    assertTrue(
        workflow.contains("release_tag recovery input must already exist on origin"),
        "manual recovery tag input should require an existing remote tag",
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
        workflow.contains("Tag already exists:"),
        "existing tags should skip duplicate tagging",
    )
    assertTrue(
        workflow.contains("GitHub release already exists:"),
        "existing GitHub releases should skip duplicate release creation",
    )
  }

  @Test
  fun `tag and release workflow should keep tag push and release creation flow`() {
    assertTrue(workflow.contains("git tag"), "release workflow should create the git tag")
    assertTrue(workflow.contains("git push origin"), "release workflow should push the release tag")
    assertTrue(
        workflow.contains("docs/release-notes/\${TAG}.md"),
        "release workflow should require the tagged release notes file",
    )
    assertTrue(
        workflow.contains("gh release create"),
        "release workflow should create a GitHub release from the tag",
    )
    assertTrue(
        workflow.contains("--notes-file"),
        "release workflow should publish the curated release notes file instead of generated notes",
    )
    assertTrue(
        workflow.contains("gh release view"),
        "release workflow should detect when the GitHub release already exists",
    )
    assertTrue(
        workflow.contains("should_create_release"),
        "release workflow should recover by creating a release even when the git tag already exists",
    )
    assertTrue(
        workflow.contains("should_publish_chain"),
        "release workflow should decide explicitly whether the publish chain should run",
    )
    assertTrue(
        workflow.contains("uses: ./.github/workflows/publish-maven-central.yml"),
        "release workflow should call the reusable Maven Central publish workflow directly",
    )
    assertTrue(
        workflow.contains("release_tag: \${{ needs.tag.outputs.tag }}"),
        "release workflow should pass the resolved release tag to the reusable publish workflow",
    )
    assertTrue(
        workflow.contains("secrets: inherit"),
        "release workflow should forward publish credentials to the reusable workflow",
    )
  }
}
