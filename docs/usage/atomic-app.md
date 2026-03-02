# atomic.app Guide

## Why Use This Module

Use `atomic.app` when you want prebuilt application APIs instead of composing every controller/service manually.

Current bundled APIs:

- app version check API
- image upload/delete API

Internally, `atomic.app` is a bundle of:

- `atomic-app:app-version`
- `atomic-app:storage-api`

## Dependency Pattern

`atomic.app` and `atomic.starter` are independent modules.

- `atomic.starter` does not include `atomic.app`.
- `atomic.app` does not require `atomic.starter` for version API.
- `atomic.app` image API requires storage beans, so starter-based setups usually add both.

Recommended (starter-based):

```kotlin
dependencies {
  implementation("com.infosung:atomic.starter:0.0.1")
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.app:0.0.1")
}
```

Local multi-module:

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-app"))
}
```

Note:

- `atomic.app.version` can work with JPA/datasource only.
- `atomic.app.image` requires storage beans (`ImageService`, `storageClients`) and JPA.
- the easiest path for image API is `atomic.starter` + `atomic.storage` properties.

## Properties

```yaml
atomic:
  app:
    version:
      enabled: true
      endpoint-path: /api/v1/version/check
      default-store-url: https://www.infosung.com
    image:
      enabled: true
      endpoint-path: /api/v1/storage/image
      default-quality: 1.0
      min-quality: 0.1
      max-quality: 1.0
      uploader-parameter-enabled: false
      uploader-parameter-name: uploaderId
```

All app APIs are disabled by default. Set each `enabled=true` explicitly.

## Version API

Default endpoint:

- `GET /api/v1/version/check`

Input resolution:

- service: `X-Service-Name` header (required)
- platform: `X-Platform` header (required)
- appVersion: `X-App-Version` header (required)

Response fields:

- `currentVersion`: latest registered version for `(service, platform)`
- `userVersion`: matched client version
- `requiredUpdate`: whether any higher `requireUpdate=true` policy exists
- `storeUrl`: forced-update target URL or `default-store-url`

Expected version policy table:

- table name: `service_version`
- entity fields used: `mainVersion`, `minorVersion`, `patchNumber`, `requireUpdate`, `platform`, `service`, `storeUrl`
- physical column names follow your JPA naming strategy (for Spring default, typically `main_version`, `minor_version`, `patch_number`, `require_update`, `store_url`)

Version API exception semantics:

- `400` when required input is missing or `appVersion` format is invalid (`x.y.z`)
- `400` when client version is not registered in policy rows
- `404` when no policy rows exist for `(service, platform)`

## Image API

Default base endpoint:

- `/api/v1/storage/image`

Endpoints:

- `POST /api/v1/storage/image/{service}/{storageService}` (`multipart/form-data`)
- `DELETE /api/v1/storage/image/{service}/{storageService}?imageId={uuid}`

> **Important (Spring Security)**
> If your service uses Spring Security, you must explicitly include the storage API path in your security rules.
> Add `POST/DELETE /api/v1/storage/image/**` (or your custom `atomic.app.image.endpoint-path/**`) to authenticated/authorized matchers.
> If this path is not included, your uploader-identity checks can be bypassed by security misconfiguration.

POST parameters:

- `file` (required multipart part)
- `quality` (optional query; default `default-quality`; allowed range `min-quality..max-quality`)
- uploader identity parameter (optional by default):
  - enabled when `atomic.app.image.uploader-parameter-enabled=true`
  - parameter name comes from `atomic.app.image.uploader-parameter-name`
  - value is stored into `ImageEntity.uploaderId`

POST response:

- persisted `ImageEntity` metadata (id, bucket, file names, urls, dimensions, sizes, status)

DELETE behavior:

- validates `imageId` UUID format
- validates that row matches `{service}` and `{storageService}`
- when uploader tracking is enabled, validates request uploader parameter equals stored `ImageEntity.uploaderId`
- deletes original/thumbnail objects from resolved storage client
- deletes metadata row

Storage client resolution:

- tries keys in order: `service:storageService`, `service::storageService`, `storageService`
- for each key, also tries exact/upper/lower variants
- if no match, returns `400`

Image API exception semantics:

- `400` invalid quality / unknown storage key / invalid UUID / path mismatch
- `400` uploader parameter missing when uploader tracking is enabled
- `404` image row not found
- `403` uploader mismatch when uploader tracking is enabled
- other upload/delete exceptions can propagate from underlying storage client or image processing layer

## Operational Checklist

- Prepare database schema for `service_version` and `image` tables before enabling APIs.
- if uploader tracking is enabled, add nullable `uploader_id` column to `image` table (or rely on JPA schema generation in non-production environments).
- choose uploader parameter name per service (for example `memberId`, `userKey`, `ownerId`) and configure `atomic.app.image.uploader-parameter-name`.
- Configure `atomic.storage.backends.*` before enabling image API.
- Keep `storageType` key naming consistent with your `{service}` and `{storageService}` path policy.
- Enforce multipart size/time limits at application layer.
