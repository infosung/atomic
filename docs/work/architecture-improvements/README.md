# Architecture Improvements Worklog

This directory tracks contract-first architecture improvement work for `atomic`.

## Goals

- Keep external usage stable while internal architecture evolves.
- Lock usage contracts with tests before implementation changes.
- Separate immediate correctness fixes from larger architectural backlog.

## Current Phase

- [Phase 1: Contract And Correctness](phase-1-contract-and-correctness.md)
- [Phase 2: Storage Delete Contract Tightening](phase-2-storage-delete-contract.md)
- [Phase 3: PostgreSQL Migration Assets](phase-3-postgresql-migration-assets.md)
- [Phase 4: Onboarding, Image Workflow, And Schema Freeze](phase-4-onboarding-image-workflow-and-schema-freeze.md)

## Scope Rules

- Prefer fixes that strengthen runtime correctness without changing public endpoints, property keys, or published usage examples.
- Require tests first for each behavior change.
- Keep external paths, property keys, documented request parameter names, and response envelopes stable unless a change is explicitly tracked as migration-impacting.
- Keep backlog items documented when they need larger design changes than the current phase allows.
