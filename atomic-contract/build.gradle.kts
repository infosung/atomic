plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  id("com.vanniktech.maven.publish")
}

description = "atomic-contract"

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
  coordinates(group.toString(), "atomic.contract", version.toString())

  pom {
    name.set("atomic.contract")
    description.set("Atomic contract module")
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
