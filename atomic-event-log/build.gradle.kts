plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.vanniktech.maven.publish)
}

description = libs.versions.moduleDescriptionEventLog.get()

java {
  toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
  withSourcesJar()
}

repositories { mavenCentral() }

dependencies {
  api(project(":atomic-contract"))

  testImplementation(libs.kotlin.test.junit5)
  testImplementation(platform(libs.jupiter.bom))
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
  coordinates(group.toString(), libs.versions.artifactEventLog.get(), version.toString())

  pom {
    name.set(libs.versions.artifactEventLog.get())
    description.set(libs.versions.pomDescriptionEventLog.get())
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
