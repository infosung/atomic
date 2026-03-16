# 2026-03-16 Multi-Perspective Review

## Purpose

This note captures a parallel review of the current `atomic` implementation from four angles:

- Spring Boot maintainer who would inherit the project
- CTO responsible for scalability, performance, and operational risk
- QA focused on failure modes and verification difficulty
- real library users evaluating adoption friction

The goal is not to restate every raw comment, but to record the consolidated findings, requested
changes, and the compromise decisions that best fit the current direction of the project.

## Review Method

- Review prompts were intentionally broad to avoid steering reviewers toward a preferred outcome.
- User-side reviewers were asked to review the currently implemented app/library as-is.
- Findings were cross-checked against the repository before being accepted into this note.
- Findings that conflicted with the current codebase were excluded from the final summary.

## Cross-Checked Corrections

Some review comments did not match the current implementation and were rejected after direct
verification.

- `storage-api` no longer returns `BaseResponse<ImageEntity>` from the controller. It now returns
  `BaseResponse<ImageResponse>`. See [AppStorageController.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/storage-api/src/main/kotlin/com/infosung/atomic/app/storage/AppStorageController.kt#L50).
- `app-version` no longer uses a full policy-list read on the main request path. It now performs
  targeted latest-version, exact-version, and higher-required-update lookups. See
  [AppVersionCheckService.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/version/src/main/kotlin/com/infosung/atomic/app/version/AppVersionCheckService.kt#L28) and
  [ServiceVersionRepository.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/version/src/main/kotlin/com/infosung/atomic/app/version/ServiceVersionRepository.kt#L13).

## Consolidated Findings

### High

- `oauth-redirect` can still degrade into process-local semantics when
  `atomic.app.oauth.redirect.store.fail-fast=false`. In multi-instance environments this can break
  one-time relay guarantees even though startup succeeds. See
  [AtomicAppOauthRedirectAutoConfiguration.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/oauth-redirect/src/main/kotlin/com/infosung/atomic/app/oauth/autoconfigure/AtomicAppOauthRedirectAutoConfiguration.kt#L242),
  [README.md](/Users/infosung/workspace/infosung/atomic/README.md#L485), and
  [atomic-app.md](/Users/infosung/workspace/infosung/atomic/docs/usage/atomic-app.md#L321).
- Image delete is now safer, but operational recovery is explicitly a host-app responsibility.
  `DELETE_PENDING` recovery is exposed as a service hook, not as a built-in scheduler or reaper.
  See
  [AppImageDeleteRecoveryService.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/storage-api/src/main/kotlin/com/infosung/atomic/app/storage/AppImageDeleteRecoveryService.kt#L13),
  [README.md](/Users/infosung/workspace/infosung/atomic/README.md#L443), and
  [atomic-app.md](/Users/infosung/workspace/infosung/atomic/docs/usage/atomic-app.md#L223).
- Adoption remains platform-sensitive. The project is still positioned around Java 25, Kotlin
  2.3.10, and Spring Boot 4.0.3, and the docs still require readers to combine information from
  several pages to form a complete onboarding path. See
  [README.md](/Users/infosung/workspace/infosung/atomic/README.md#L5),
  [README.md](/Users/infosung/workspace/infosung/atomic/README.md#L61),
  [overview.md](/Users/infosung/workspace/infosung/atomic/docs/usage/overview.md#L50), and
  [quick-start.md](/Users/infosung/workspace/infosung/atomic/docs/usage/quick-start.md#L14).

### Medium

- Schema guards are still light. Several string fields remain `VARCHAR(255)`, and
  `service_version` still has no explicit semantic-version uniqueness protection. See
  [image.sql](/Users/infosung/workspace/infosung/atomic/atomic-app/storage-api/src/main/resources/META-INF/atomic/sql/postgresql/image.sql#L1) and
  [service_version.sql](/Users/infosung/workspace/infosung/atomic/atomic-app/version/src/main/resources/META-INF/atomic/sql/postgresql/service_version.sql#L1).
- `callback-binding.mode=relaxed` is a reasonable UX option, but it increases browser-state
  verification complexity. Multi-tab, back-navigation, and stale-cookie behavior need stronger
  support guidance. See
  [AtomicAppOauthRedirectProperties.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/oauth-redirect/src/main/kotlin/com/infosung/atomic/app/oauth/autoconfigure/AtomicAppOauthRedirectProperties.kt#L78) and
  [atomic-app.md](/Users/infosung/workspace/infosung/atomic/docs/usage/atomic-app.md#L258).
- Image processing still runs synchronously on the request thread. That is simple and acceptable
  for moderate workloads, but the repo does not yet define a supported throughput envelope or an
  official async/offload threshold. See
  [AppImageApiService.kt](/Users/infosung/workspace/infosung/atomic/atomic-app/storage-api/src/main/kotlin/com/infosung/atomic/app/storage/AppImageApiService.kt#L76) and
  [ImageService.kt](/Users/infosung/workspace/infosung/atomic/atomic-storage/src/main/kotlin/com/infosung/atomic/storage/image/ImageService.kt#L99).
- Several features still depend on cross-module prerequisites that are documented but not
  summarized in a single “feature responsibility matrix.” Reviewers consistently saw this as a
  maintainability and onboarding tax.

## Requested Requirements

- Publish a clearer runtime support matrix for Java, Spring Boot, and Kotlin.
- Add a narrow-module-first onboarding page that shows published artifact coordinates and minimum
  working configuration for each app module.
- Document a feature responsibility matrix that states, for each module, whether it needs:
  database schema, shared store, scheduler/admin job, security rule changes, and host-side
  exception policy decisions.
- Separate `oauth-redirect` guidance into at least two tracks:
  `single-node/local` and `multi-instance/production`.
- Provide an explicit operational runbook for `DELETE_PENDING`, including recovery invocation,
  retry expectations, and recommended metrics/alerts.
- Review `image` and `service_version` schema constraints, especially string column lengths and
  version-policy data integrity.
- State the supported browser/UX expectations for callback-binding `strict`, `relaxed`, and
  `disabled`.
- Document the supported workload envelope for synchronous image processing and the threshold that
  should trigger async/offload adoption.

## Conflicting Demands And Compromises

### Convenience vs Safety

- User-side reviewers wanted easier adoption and less configuration friction.
- CTO and QA reviewers wanted stronger guarantees and fewer silent degradations.
- Compromise:
  keep the current conservative defaults, but make convenience modes explicit opt-ins and clearly
  labeled as local/dev-oriented when they reduce guarantees.

### Security vs UX

- Some reviewers favored keeping callback-binding as strict as possible.
- Others preferred smoother multi-tab and retry UX.
- Compromise:
  keep `strict` as the default, keep `relaxed` as an explicit opt-in, and treat `disabled` as a
  deliberate escape hatch rather than a hidden behavior.

### Library Scope vs Operational Ownership

- Some reviewers wanted more built-in automation for recovery paths.
- Others preferred keeping the library out of host scheduling/orchestration decisions.
- Compromise:
  keep recovery execution under host-app control, but strengthen the official runbook, examples,
  metrics, and alerts so teams do not each invent their own incomplete pattern.

### Simple Synchronous Flow vs Future Scale

- Current sync image handling is easy to understand and integrate.
- Reviewers responsible for operations and scale want clearer limits and an eventual async path.
- Compromise:
  keep the synchronous implementation for now, but document its intended envelope and move the
  async/offload design into a deliberate later phase instead of leaving it implicit.

## Recommended Next Actions

- Close the remaining medium-priority backlog item by documenting synchronous image-processing
  limits and async/offload thresholds.
- Add a feature responsibility matrix to reduce onboarding and handoff friction.
- Review schema guardrails for string length and version-policy uniqueness.
- Expand production-focused guidance for `oauth-redirect`, especially around store fallback and
  callback-binding mode selection.

## Current Backlog Alignment

This review supports the current backlog direction and suggests that the following areas still
deserve attention after the remaining medium-priority documentation work:

- selective hexagonal boundaries inside `atomic-app/*`
- async image pipeline definition when throughput targets justify it
- starter/bundle naming review after onboarding guidance stabilizes
