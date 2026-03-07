# Atomic Quick Start (10-Minute Minimal Setup)

This guide focuses only on getting a working flow quickly.

## 0) Before You Start (Common)

| Item | Minimum |
|---|---|
| App type | Spring Boot Web application |
| Tested baseline | Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3` |
| Database | Required for `version` and `image` tracks. The `oauth redirect` track may also require **DataSource setup** or **JDBC auto-config exclusion** because `atomic.app` includes JPA-based modules. |
| Module dependencies | Use the track table below as-is. |

Dependency notation:
- Gradle snippets below assume a local multi-module setup (`project(":...")`).
- For published artifact coordinates, see [README Dependency Setup](../../README.md).

Quick decision:
- If you are still pre-production, validate behavior first with this document.
- For production or multi-instance deployment, continue with [advanced-playbook](advanced-playbook.md).

---

## 1) Three Minimal Adoption Tracks

| Track | When to choose | Minimum dependencies (Gradle) | App prerequisites | Minimum properties |
|---|---|---|---|---|
| `A. version-only` | You only need a version check API quickly | `implementation(project(":atomic-app"))` | DataSource/JPA + `service_version` table | `atomic.app.version.enabled=true` |
| `B. image API` | You want common image upload/delete API first | `implementation(project(":atomic-starter"))`<br>`implementation(project(":atomic-app"))`<br>`implementation(project(":atomic-storage"))` | DataSource/JPA + `image` table | `atomic.app.image.enabled=true` + minimum `atomic.storage.backends.*` |
| `C. oauth redirect relay API` | You want to return `relayCode` instead of exposing OAuth callback tokens directly to frontend | `implementation(project(":atomic-starter"))`<br>`implementation(project(":atomic-app"))`<br>`implementation(project(":atomic-spring-oauth2"))` | Login API that consumes `relayCode` + DataSource/JPA or JDBC auto-config exclusion | `atomic.app.oauth.redirect.enabled=true` + `atomic.app.oauth.redirect.allowed-redirect-uri-prefixes` + `atomic.oauth2.state.signing-secret` + provider minimum values |

---

## 2) Minimal YAML by Track

### A. version-only

```yaml
atomic:
  app:
    version:
      enabled: true
```

### B. image API

```yaml
atomic:
  app:
    image:
      enabled: true
  storage:
    backends:
      S3:
        type: s3
        region: ap-northeast-2
        bucket: your-bucket
        cdn: https://cdn.example.com
```

### C. oauth redirect relay API (Google single-client minimal example)

> Warning: secret-like sample values below are for local bootstrap only. Replace before any shared/staging/prod deployment.

```yaml
spring:
  autoconfigure:
    # Use this only for quick-start environments without DB
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

atomic:
  app:
    oauth:
      redirect:
        enabled: true
        store:
          type: in-memory # quick-start only; production usually prefers entity or shared backend
        allowed-redirect-uri-prefixes:
          - https://web.example.com/oauth
  oauth2:
    state:
      in-memory-store:
        enabled: true # single-node quick-start; for multi-instance use custom/shared store
      signing-secret: CHANGE_ME_WITH_STRONG_RANDOM_STATE_SECRET_AT_LEAST_32_BYTES
    providers:
      google:
        enabled: true
        client-id: your-google-client-id
        client-secret: your-google-client-secret
        server-redirect-uri: https://api.example.com/oauth/callback/google
```

Notes:
- Set `allowed-redirect-uri-prefixes` even in quick-start when possible; it is mandatory for production.
- `spring.autoconfigure.exclude` is a temporary quick-start shortcut for non-DB environments. For production, configure DataSource/store policy explicitly (`entity/cache/custom`).
- If Spring Security is enabled, explicitly configure `permitAll` for redirect/callback endpoints and CSRF policy for Apple `POST` callback path.

---

## 3) Fail-Fast Checklist (Immediate Verification)

| Track | What to verify immediately |
|---|---|
| `A. version-only` | On `GET /api/v1/version/check`, confirm headers (`X-Service-Name`, `X-Platform`, `X-App-Version`) are present and `service_version` table/rows are ready |
| `B. image API` | Confirm `POST /api/v1/storage/image/{service}/{storageService}` is not `404`, and storage backend key (`S3`, etc.) matches request path values |
| `C. oauth redirect relay API` | Confirm `GET /oauth/redirect/google?redirectUri=...` returns redirect, and provider console redirect URI exactly matches `https://{host}/oauth/callback/google` |

Common failure causes:
- Missing module dependencies while `enabled=true`
- OAuth `signing-secret` length is below 32 bytes
- Callback URI mismatch between provider console and server config

---

## 4) Detailed Docs

- [atomic-app](atomic-app.md)
- [atomic-starter](atomic-starter.md)
- [environment-variables](environment-variables.md)
- [advanced-playbook](advanced-playbook.md)
