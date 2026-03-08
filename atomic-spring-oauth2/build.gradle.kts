plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.springboot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.vanniktech.maven.publish)
}

group = libs.versions.projectGroup.get()

description = libs.versions.moduleDescriptionSpringOauth2.get()

java {
  toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
  withSourcesJar()
  withJavadocJar()
}

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

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(group.toString(), libs.versions.artifactSpringOauth2.get(), version.toString())

  pom {
    name.set(libs.versions.artifactSpringOauth2.get())
    description.set(libs.versions.pomDescriptionSpringOauth2.get())
    url.set(libs.versions.projectHomepageUrl.get())
    licenses {
      license {
        name.set(libs.versions.licenseApacheName.get())
        url.set(libs.versions.licenseApacheUrl.get())
      }
    }
    developers {
      developer {
        id.set(libs.versions.developerId.get())
        name.set(libs.versions.developerName.get())
      }
    }
    scm {
      url.set(libs.versions.projectHomepageUrl.get())
      connection.set(libs.versions.scmConnection.get())
      developerConnection.set(libs.versions.scmDeveloperConnection.get())
    }
  }
}
