package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.autoconfigure.AtomicAppVersionProperties
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
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
    val idField = ServiceVersionEntity::class.java.getDeclaredField("id")
    val generatedValue = idField.getAnnotation(GeneratedValue::class.java)

    assertEquals("service_version", entity.name)
    assertNotNull(idField.getAnnotation(Id::class.java))
    assertEquals(GenerationType.IDENTITY, generatedValue.strategy)
  }

  @Test
  fun `version request and result payload fields should remain stable`() {
    assertEquals(
        listOf("service", "platform", "appVersion"),
        VersionCheckRequest::class.primaryConstructor!!.parameters.mapNotNull { it.name },
    )
    assertEquals(
        listOf("currentVersion", "userVersion", "requiredUpdate", "storeUrl"),
        VersionCheckResult::class.primaryConstructor!!.parameters.mapNotNull { it.name },
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
  fun `app version check service public methods should remain stable`() {
    assertEquals(
        listOf("checkVersion(VersionCheckRequest):VersionCheckResult"),
        publicSignatures(AppVersionCheckService::class.java),
    )
  }

  @Test
  fun `version response should serialize with stable boot json contract`() {
    contextRunner.run { context ->
      val objectMapper = context.getBean(ObjectMapper::class.java)
      val response =
          BaseResponse.ok(
              VersionCheckResult(
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
