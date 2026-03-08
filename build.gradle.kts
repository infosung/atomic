import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.publish.tasks.GenerateModuleMetadata

val ktfmtVersion = libs.versions.ktfmt.get()
val projectGroup = libs.versions.projectGroup.get()
val projectVersion = libs.versions.projectVersion.get()

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.spring) apply false
  alias(libs.plugins.springboot) apply false
  alias(libs.plugins.spring.dependency.management) apply false
  alias(libs.plugins.vanniktech.maven.publish) apply false
  alias(libs.plugins.spotless) apply false
}

allprojects {
  repositories { mavenCentral() }

  apply(plugin = "com.diffplug.spotless")

  tasks.configureEach {
    if (name.startsWith("spotless")) {
      notCompatibleWithConfigurationCache(
          "Spotless tasks are not configuration-cache compatible in this build yet.")
    }
  }

  extensions.configure<SpotlessExtension> {
    kotlin {
      target("src/**/*.kt")
      targetExclude("**/build/**")
      ktfmt(ktfmtVersion)
    }

    kotlinGradle {
      target("*.gradle.kts", "settings.gradle.kts")
      ktfmt(ktfmtVersion)
    }

    format("misc") {
      target(
          ".gitignore",
          "**/*.md",
          "**/*.yaml",
          "**/*.yml",
          "**/*.json",
          "**/*.json5",
          "**/*.xml",
          "**/*.toml",
          "**/*.properties",
          "**/*.sh",
          "**/*.bash",
          "**/*.zsh",
          "**/*.sql",
      )
      targetExclude("**/build/**", ".gradle/**", ".gradle-user/**", ".idea/**", "**/.idea/**")
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
}

subprojects {
  group = projectGroup
  version = projectVersion

  // Gradle 9 validates publication task inputs strictly.
  plugins.withId("com.vanniktech.maven.publish") {
    tasks.withType<GenerateModuleMetadata>().configureEach {
      dependsOn(tasks.matching { it.name == "plainJavadocJar" })
    }
  }
}
