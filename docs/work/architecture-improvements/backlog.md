# Architecture Improvement Backlog

These items came out of the review but are intentionally not part of Phase 1.

## High Priority

- no open high-priority backlog items at the moment

## Medium Priority

- add callback-binding policy mode so host apps can choose stricter security or more permissive UX
  - candidate shape: `strict | relaxed | disabled`
  - keep the default conservative, but allow multi-tab/back-navigation-friendly opt-in
- document synchronous request-thread image processing limits and async/offload threshold

## Long-Term Architecture

- selectively introduce hexagonal boundaries inside `atomic-app/*` modules
- define async/offloaded image processing path if higher upload concurrency becomes a target
- revisit starter and bundle naming once module boundaries and onboarding guidance stabilize
