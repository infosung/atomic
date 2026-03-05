plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.vanniktech.maven.publish)
}

description = libs.versions.moduleDescriptionStarter.get()

java {
  toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
  withSourcesJar()
  withJavadocJar()
}

repositories { mavenCentral() }

dependencies {
  implementation(libs.spring.boot.autoconfigure)

  compileOnly(projects.atomicContract)
  compileOnly(projects.atomicStorage)
  compileOnly(projects.atomicHeartbeat)
  compileOnly(projects.atomicSpringWeb)
  compileOnly(projects.atomicSpringIdempotency)
  compileOnly(projects.atomicSpringSecurity)
  compileOnly(projects.atomicSpringOauth2)
  compileOnly(libs.aws.sdk.s3)
  compileOnly(libs.spring.boot.starter.restclient)
  compileOnly(libs.google.api.client)
  compileOnly(libs.spring.data.redis)
  compileOnly(libs.jackson.module.kotlin)
  compileOnly("org.springframework:spring-jdbc")

  annotationProcessor(libs.spring.boot.configuration.processor)

  testImplementation(projects.atomicContract)
  testImplementation(projects.atomicStorage)
  testImplementation(projects.atomicHeartbeat)
  testImplementation(projects.atomicSpringWeb)
  testImplementation(projects.atomicSpringIdempotency)
  testImplementation(projects.atomicSpringSecurity)
  testImplementation(projects.atomicSpringOauth2)
  testImplementation(libs.spring.data.redis)
  testImplementation("org.springframework:spring-jdbc")
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

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(group.toString(), libs.versions.artifactStarter.get(), version.toString())

  pom {
    name.set(libs.versions.artifactStarter.get())
    description.set(libs.versions.pomDescriptionStarter.get())
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
