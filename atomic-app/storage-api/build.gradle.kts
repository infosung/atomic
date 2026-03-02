plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.dependency.management)
}

java {
  toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
  withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-storage"))

  implementation(libs.kotlin.reflect)
  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.spring.boot.starter.data.jpa)

  annotationProcessor(libs.spring.boot.configuration.processor)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.data.jpa.test)
  testImplementation(libs.spring.boot.jdbc.test)
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.aws.sdk.s3)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(platform(libs.jupiter.bom))
  testRuntimeOnly(libs.postgresql)
  testRuntimeOnly(libs.jupiter.launcher)
  testRuntimeOnly(libs.jupiter)
}

dependencyManagement { imports { mavenBom(libs.spring.boot.dependencies.bom.get().toString()) } }

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

tasks.withType<Test> { useJUnitPlatform() }
