package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionCheckResponseDto
import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionController
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity
import com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository
import com.infosung.atomic.app.version.application.exception.AppVersionApplicationException
import com.infosung.atomic.app.version.application.exception.AppVersionErrorCode
import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.autoconfigure.AtomicAppVersionProperties
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.lang.reflect.Modifier
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper

class AppVersionPublicContractTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))

  @Test
  fun `service version entity constructor contract should remain stable`() {
    val parameterNames =
        ServiceVersionEntity::class.primaryConstructor!!.parameters.mapNotNull { it.name }

    assertEquals(
        listOf(
            "id",
            "mainVersion",
            "minorVersion",
            "patchNumber",
            "requireUpdate",
            "platform",
            "service",
            "storeUrl",
            "createdAt",
        ),
        parameterNames,
    )
  }

  @Test
  fun `service version entity schema annotations should remain stable`() {
    val entity = ServiceVersionEntity::class.java.getAnnotation(Entity::class.java)
    val table = ServiceVersionEntity::class.java.getAnnotation(Table::class.java)
    val idField = ServiceVersionEntity::class.java.getDeclaredField("id")
    val generatedValue = idField.getAnnotation(GeneratedValue::class.java)
    val mainVersionField = ServiceVersionEntity::class.java.getDeclaredField("mainVersion")
    val patchNumberField = ServiceVersionEntity::class.java.getDeclaredField("patchNumber")
    val requireUpdateField = ServiceVersionEntity::class.java.getDeclaredField("requireUpdate")
    val storeAvailableField = ServiceVersionEntity::class.java.getDeclaredField("storeAvailable")
    val platformField = ServiceVersionEntity::class.java.getDeclaredField("platform")
    val serviceField = ServiceVersionEntity::class.java.getDeclaredField("service")
    val storeUrlField = ServiceVersionEntity::class.java.getDeclaredField("storeUrl")

    assertEquals("service_version", entity.name)
    assertEquals("service_version", table.name)
    assertEquals(1, table.uniqueConstraints.size)
    assertEquals(
        "uq_service_version_service_platform_semver",
        table.uniqueConstraints.single().name,
    )
    assertEquals(
        listOf("service", "platform", "main_version", "minor_version", "patch_number"),
        table.uniqueConstraints.single().columnNames.toList(),
    )
    assertNotNull(idField.getAnnotation(Id::class.java))
    assertEquals(GenerationType.IDENTITY, generatedValue.strategy)
    assertEquals("id", idField.getAnnotation(Column::class.java).name)
    assertEquals("main_version", mainVersionField.getAnnotation(Column::class.java).name)
    assertEquals("patch_number", patchNumberField.getAnnotation(Column::class.java).name)
    assertEquals("require_update", requireUpdateField.getAnnotation(Column::class.java).name)
    assertEquals("store_available", storeAvailableField.getAnnotation(Column::class.java).name)
    assertEquals("platform", platformField.getAnnotation(Column::class.java).name)
    assertEquals(255, platformField.getAnnotation(Column::class.java).length)
    assertEquals("service", serviceField.getAnnotation(Column::class.java).name)
    assertEquals(255, serviceField.getAnnotation(Column::class.java).length)
    assertEquals("store_url", storeUrlField.getAnnotation(Column::class.java).name)
    assertEquals("TEXT", storeUrlField.getAnnotation(Column::class.java).columnDefinition)
  }

  @Test
  fun `version response dto fields should remain stable`() {
    assertEquals(
        listOf("currentVersion", "userVersion", "requiredUpdate", "storeUrl"),
        AppVersionCheckResponseDto::class.primaryConstructor!!.parameters.mapNotNull { it.name },
    )
  }

  @Test
  fun `atomic app version properties public keys should remain stable`() {
    assertEquals(
        listOf("defaultStoreUrl", "enabled", "endpointPath").sorted(),
        AtomicAppVersionProperties::class.memberProperties.map { it.name }.sorted(),
    )
  }

  @Test
  fun `app version use case public methods should remain stable`() {
    assertEquals(
        listOf("check(String, String, String):VersionCheckDecision"),
        publicSignatures(CheckAppVersionUseCase::class.java),
    )
  }

  @Test
  fun `version web entry type should remain exported from adapter in web`() {
    assertEquals(
        "com.infosung.atomic.app.version.adapter.in.web.AppVersionController",
        AppVersionController::class.java.name,
    )
  }

  @Test
  fun `version public error seams should remain exported from application exception`() {
    assertEquals(
        "com.infosung.atomic.app.version.application.exception.AppVersionApplicationException",
        AppVersionApplicationException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.application.exception.InvalidAppVersionException",
        InvalidAppVersionException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException",
        VersionPolicyNotFoundException::class.java.name,
    )
    assertEquals(
        listOf(
            "VERSION_SERVICE_NAME_REQUIRED",
            "VERSION_PLATFORM_REQUIRED",
            "VERSION_APP_VERSION_REQUIRED",
            "VERSION_INVALID_APP_VERSION",
            "VERSION_POLICY_NOT_FOUND",
        ),
        AppVersionErrorCode.entries.map { it.name },
    )
  }

  @Test
  fun `version persistence seam should remain exported from adapter out persistence`() {
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionEntity",
        ServiceVersionEntity::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.version.adapter.out.persistence.ServiceVersionRepository",
        ServiceVersionRepository::class.java.name,
    )
  }

  @Test
  fun `version response should serialize with stable boot json contract`() {
    contextRunner.run { context ->
      val objectMapper = context.getBean(ObjectMapper::class.java)
      val response =
          BaseResponse.ok(
              AppVersionCheckResponseDto(
                  currentVersion = "2.0.0",
                  userVersion = "1.5.0",
                  requiredUpdate = true,
                  storeUrl = "https://play.google.com/store/apps/details?id=atomic",
              ),
          )

      val json = objectMapper.readTree(objectMapper.writeValueAsString(response))

      assertEquals("OK", json["code"].stringValue())
      assertEquals("Success", json["message"].stringValue())
      assertEquals("2.0.0", json["data"]["currentVersion"].stringValue())
      assertEquals("1.5.0", json["data"]["userVersion"].stringValue())
      assertEquals(true, json["data"]["requiredUpdate"].booleanValue())
      assertEquals(
          "https://play.google.com/store/apps/details?id=atomic",
          json["data"]["storeUrl"].stringValue(),
      )
    }
  }

  private fun publicSignatures(type: Class<*>): List<String> {
    return type.declaredMethods
        .filter {
          Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("\$default")
        }
        .map { method ->
          val parameters = method.parameterTypes.joinToString(", ") { it.simpleName }
          "${method.name}($parameters):${method.returnType.simpleName}"
        }
        .sorted()
  }
}
