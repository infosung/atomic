enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "atomic"

include(
    ":atomic-app",
    ":atomic-app:app-version",
    ":atomic-app:oauth-redirect",
    ":atomic-app:storage-api",
    ":atomic-event-log",
    ":atomic-event-log:parquet",
    ":atomic-event-log:iceberg",
    ":atomic-event-log:duckdb",
    ":atomic-event-log:spring-web",
    ":atomic-heartbeat",
    ":atomic-starter",
    ":atomic-contract",
    ":atomic-storage",
    ":atomic-spring-idempotency",
    ":atomic-spring-oauth2",
    ":atomic-spring-web",
    ":atomic-spring-security",
)

project(":atomic-app:app-version").projectDir = file("atomic-app/version")
