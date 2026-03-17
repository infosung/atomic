# `v0.0.3` Roadmap

`v0.0.3` should stay a hardening and cleanup release, not another broad behavior change release.

## Recommended Scope

- harden schema widths for operationally long values
  - revisit `image` string columns such as `url`, `thumbnail_url`, `bucket`, `storage_service`,
    `storage_type`, and file-name fields
  - revisit `service_version.store_url`
  - ship matching SQL assets, entity mappings, and migration tests
- improve `storage-api` operator hooks around `DELETE_PENDING`
  - add clearer recovery observability such as metrics/logging hooks or an example scheduler path
  - keep the library free of a mandatory built-in reaper
- add stronger `oauth-redirect` production presets and examples
  - make `single-node` vs `multi-instance` store policy choices easier to apply correctly
  - document `callback-binding` mode and `store.fail-fast` combinations as clearer deployment presets
- simplify onboarding and module naming
  - make `atomic.app` vs narrow-module adoption easier to understand
  - revisit starter/bundle naming now that `0.0.2` release docs have stabilized

## Later, Not Required For `v0.0.3`

- selectively introduce hexagonal boundaries inside `atomic-app/*` modules
- define an optional async/offloaded image pipeline when throughput targets justify it
