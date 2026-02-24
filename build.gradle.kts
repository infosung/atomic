import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.spring) apply false
  alias(libs.plugins.springboot) apply false
  alias(libs.plugins.spring.dependency.management) apply false
  id("com.vanniktech.maven.publish") version "0.34.0" apply false
  id("com.diffplug.spotless") version "7.2.1" apply false
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
      ktfmt("0.54")
    }

    kotlinGradle {
      target("*.gradle.kts", "settings.gradle.kts")
      ktfmt("0.54")
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
  group = "com.infosung"
  version = "0.0.1"
}
