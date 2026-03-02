enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "atomic"

include(
    ":atomic-starter",
    ":atomic-contract",
    ":atomic-storage",
    ":atomic-spring-oauth2",
    ":atomic-spring-web",
    ":atomic-spring-security",
)
