plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.vanniktech.maven.publish)
}

description = libs.versions.moduleDescriptionAppVersion.get()

java {
  toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
  withSourcesJar()
  withJavadocJar()
}

repositories { mavenCentral() }

dependencies {
  implementation(project(":atomic-contract"))

  implementation(libs.kotlin.reflect)
  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.webmvc)
  implementation(libs.spring.boot.starter.data.jpa)

  annotationProcessor(libs.spring.boot.configuration.processor)

  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.data.jpa.test)
  testImplementation(libs.spring.boot.jdbc.test)
  testImplementation(libs.spring.boot.testcontainers)
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

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(group.toString(), libs.versions.artifactAppVersion.get(), version.toString())

  pom {
    name.set(libs.versions.artifactAppVersion.get())
    description.set(libs.versions.pomDescriptionAppVersion.get())
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
