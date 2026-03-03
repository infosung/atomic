enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "atomic"

include(
    ":atomic-app",
    ":atomic-app:app-version",
    ":atomic-app:oauth-redirect",
    ":atomic-app:storage-api",
    ":atomic-starter",
    ":atomic-contract",
    ":atomic-storage",
    ":atomic-spring-oauth2",
    ":atomic-spring-web",
    ":atomic-spring-security",
)

project(":atomic-app:app-version").projectDir = file("atomic-app/version")
