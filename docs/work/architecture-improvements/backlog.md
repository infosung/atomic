# Architecture Improvement Backlog

These items came out of the review but are intentionally not part of Phase 1.

## High Priority

- clarify public adoption guidance so `atomic.app` is documented as a convenience bundle, not the default narrow-path recommendation
- resolve image delete consistency risk between storage deletion and metadata deletion after the delete-time storage fallback is removed
- decide whether oauth relay fallback to process-local memory should remain available in multi-instance deployments

## Medium Priority

- separate external image response DTOs from `ImageEntity`
- document whether `BaseExceptionHandler` is mandatory for official HTTP error semantics
- decide whether to freeze `service_version` / `image` physical column names in code (`@Table` / `@Column`) or keep relying on documented default naming strategy
- review version-check query strategy for large policy sets
- confirm whether callback-binding cookie lifecycle should be tightened further after multi-tab UX validation
- review whether version policy resolution should avoid in-memory post-processing at larger scale
- document synchronous request-thread image processing limits and async/offload threshold

## Long-Term Architecture

- selectively introduce hexagonal boundaries inside `atomic-app/*` modules
- define async/offloaded image processing path if higher upload concurrency becomes a target
- revisit starter and bundle naming once module boundaries and onboarding guidance stabilize
