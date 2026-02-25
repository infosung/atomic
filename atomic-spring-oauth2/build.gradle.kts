plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.springboot)
  alias(libs.plugins.spring.dependency.management)
}

group = libs.versions.oauthModuleGroup.get()

java { toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) } }

repositories { mavenCentral() }

dependencies {
  implementation(projects.atomicContract)

  implementation(libs.spring.boot.starter.restclient)
  implementation(libs.spring.security.oauth2.jose)
  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  testImplementation(libs.spring.boot.starter.restclient.test)
  testImplementation(libs.kotlin.test.junit5)
  testRuntimeOnly(libs.jupiter.launcher)

  implementation(libs.google.api.client)
  implementation(libs.bouncycastle)
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.bootJar { enabled = false }

tasks.jar { enabled = true }
