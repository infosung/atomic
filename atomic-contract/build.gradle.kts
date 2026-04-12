plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.vanniktech.maven.publish)
}

description = libs.versions.moduleDescriptionContract.get()

java {
  toolchain {
    languageVersion =
        JavaLanguageVersion.of(
            libs.versions.java.get().toInt(),
        )
  }
  withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
  testImplementation(platform(libs.spring.boot.dependencies.bom))
  testImplementation(projects.atomicApp.appVersion)
  testImplementation(projects.atomicApp.storageApi)
  testImplementation(projects.atomicApp.oauthRedirect)
  testImplementation(projects.atomicSpringSecurity)
  testImplementation(projects.atomicSpringWeb)
  testImplementation(projects.atomicSpringIdempotency)
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.snakeyaml)
  testImplementation(libs.spring.jdbc)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.mysql)
  testImplementation(libs.testcontainers.mariadb)
  testImplementation(libs.testcontainers.oracle.free)
  testImplementation(libs.mysql)
  testImplementation(libs.mariadb)
  testImplementation(platform(libs.jupiter.bom))
  testRuntimeOnly(libs.h2)
  testRuntimeOnly(libs.oracle.jdbc)
  testRuntimeOnly(libs.jupiter.launcher)
  testRuntimeOnly(libs.jupiter)
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
  }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(group.toString(), libs.versions.artifactContract.get(), version.toString())

  pom {
    name.set(libs.versions.artifactContract.get())
    description.set(libs.versions.pomDescriptionContract.get())
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

tasks.withType<Test> { useJUnitPlatform() }
