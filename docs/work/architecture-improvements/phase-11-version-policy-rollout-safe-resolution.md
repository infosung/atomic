# Phase 11: Version Policy Rollout-Safe Resolution

## Why

The current version API treats an unregistered client version as `400 Bad Request`.

That is too strict for real mobile rollout behavior:

- App Store / Play Store releases can appear gradually.
- Review or internal-distribution builds can call production APIs before store rollout completes.
- Operators can prepare future version rows before the target version is safe to force-update.

For `v0.0.2`, the version API should behave like an update-policy API, not like a strict version
whitelist endpoint.

## Goals

- stop rejecting semantically valid but unregistered client versions with `400`
- add a policy-level way to mark whether a version is safe to expose as the current store target
- evaluate `requiredUpdate` only against store-available target rows
- keep the public HTTP path and JSON field names unchanged

## Non-Goals

- redesigning the version API payload shape
- adding store-specific rollout percentages or staged rollout metadata
- adding background synchronization with App Store / Play Store

## Target Contract

- invalid version format still returns `400`
- missing required headers still return `400`
- no policy rows for `(service, platform)` still returns `404`
- semantically valid but unregistered client versions return `200`
- `requiredUpdate=true` is based only on higher version rows that are marked store-available
- `currentVersion` uses the latest store-available version when one exists
- if no store-available row exists yet, fall back to the latest registered version and log a warning

## Schema Change

Add a new `service_version.store_available` column:

- type: `BOOLEAN`
- default: `TRUE`
- meaning: this version is safe to expose as the current store target and safe to use as a forced
  update target

This default keeps older deployments compatible until operators start using the new flag
intentionally.

## Implementation Plan

1. Add failing tests for unregistered-but-valid client versions.
2. Add failing tests for store-available filtering in `currentVersion` and `requiredUpdate`.
3. Add failing schema/public-contract tests for `store_available`.
4. Implement repository and service changes.
5. Update public docs and migration guidance for the new policy semantics.
