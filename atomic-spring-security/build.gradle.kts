plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.dependency.management)
  id("com.vanniktech.maven.publish")
}

description = "atomic-spring-security"

java {
  toolchain {
    languageVersion =
        JavaLanguageVersion.of(
            libs.versions.java.get().toInt(),
        )
  }
  withSourcesJar()
  withJavadocJar()
}

repositories { mavenCentral() }

dependencies {
  api(projects.atomicContract)

  api(libs.spring.boot.starter.webmvc)
  api(libs.spring.boot.starter.security)
  implementation(libs.jackson.module.kotlin)

  api(libs.jjwt.api)
  runtimeOnly(libs.jjwt.impl)
  runtimeOnly(libs.jjwt.jackson)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testImplementation(libs.spring.boot.starter.security.test)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(platform(libs.jupiter.bom))
  testRuntimeOnly(libs.jupiter.launcher)
  testRuntimeOnly(libs.jupiter)
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot.get()}")
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(group.toString(), "atomic.spring.security", version.toString())

  pom {
    name.set("atomic.spring.security")
    description.set("Atomic Spring Security module")
    url.set("https://github.com/infosung/atomic")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        id.set("infosung")
        name.set("infosung")
      }
    }
    scm {
      url.set("https://github.com/infosung/atomic")
      connection.set("scm:git:git://github.com/infosung/atomic.git")
      developerConnection.set("scm:git:ssh://git@github.com/infosung/atomic.git")
    }
  }
}

tasks.withType<Test> { useJUnitPlatform() }
