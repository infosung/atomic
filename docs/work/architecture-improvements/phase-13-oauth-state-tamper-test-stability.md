# Phase 13: OAuth State Tamper Test Stability

## Why This Phase Exists

`OauthStateManagerTest` currently mutates only the last character of the compact JWT string when it
tries to simulate tampering.

That is not deterministic because the last character of an unpadded Base64URL segment can change
without changing the decoded bytes that are actually verified by the signature checker.

As a result, CI can sometimes observe a token string change that still verifies successfully.

## Goal

Make the tamper test deterministic across Kotlin/Spring Boot/JDK matrix runs.

## Non-Goals

- No change to `OauthStateManager` runtime behavior
- No change to OAuth state signing or verification semantics
- No public API change

## Contract To Keep

When the signed OAuth state token bytes are actually modified, `verifyState(...)` must fail with
`InvalidOauthStateException`.

## TDD Plan

1. Replace the current fragile tamper strategy with one that changes a significant character inside the
   JWT payload or signature segment.
2. Keep the assertion focused on the same external contract: tampered token verification must fail.
3. Re-run the focused oauth2 test target after the test change.
