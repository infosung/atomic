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
  implementation(project(":atomic-spring-oauth2"))

  implementation(libs.kotlin.reflect)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.security.oauth2.jose)
  implementation(libs.spring.boot.starter.webmvc)
  implementation("org.springframework:spring-jdbc")
  implementation("org.springframework:spring-tx")

  annotationProcessor(libs.spring.boot.configuration.processor)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(platform(libs.jupiter.bom))
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
