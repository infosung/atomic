package com.infosung.atomic.spring.web.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.AfterEach
import tools.jackson.databind.ObjectMapper

class JsonTransferTest {
  private val objectMapper = ObjectMapper()
  private val jsonTransfer = JsonTransfer()

  @AfterEach
  fun resetSensitiveKeyRegex() {
    jsonTransfer.resetSensitiveKeyRegex()
  }

  @Test
  fun `mapToJson should return null for null or empty`() {
    assertNull(jsonTransfer.mapToJson(null))
    assertNull(jsonTransfer.mapToJson(emptyMap<String, String>()))
  }

  @Test
  fun `mapToJson should serialize nested structures`() {
    val json =
        jsonTransfer.mapToJson(
            mapOf(
                "name" to "totp",
                "numbers" to listOf(1, 2, 3),
                "nested" to mapOf("ok" to true),
            ),
        )

    val node = objectMapper.readTree(json)
    assertEquals("\"totp\"", node["name"].toString())
    assertEquals(3, node["numbers"].size())
    assertEquals(true, node["nested"]["ok"].asBoolean())
  }

  @Test
  fun `listToJson and arrayToJson should serialize values`() {
    val listJson = jsonTransfer.listToJson(listOf("a", "b"))
    val arrayJson = jsonTransfer.arrayToJson(arrayOf(1, 2, 3))

    val listNode = objectMapper.readTree(listJson)
    val arrayNode = objectMapper.readTree(arrayJson)

    assertEquals("\"a\"", listNode[0].toString())
    assertEquals(3, arrayNode.size())
  }

  @Test
  fun `mapToJson should mask sensitive keys recursively`() {
    val json =
        jsonTransfer.mapToJson(
            mapOf(
                "password" to "1234",
                "nested" to mapOf("accessToken" to "abcdef"),
                "items" to listOf(mapOf("apiKey" to "secret-key")),
                "name" to "totp",
            ),
        )

    val node = objectMapper.readTree(json)
    assertEquals("\"***\"", node["password"].toString())
    assertEquals("\"***\"", node["nested"]["accessToken"].toString())
    assertEquals("\"***\"", node["items"][0]["apiKey"].toString())
    assertEquals("\"totp\"", node["name"].toString())
  }

  @Test
  fun `mapToJson should not throw when payload is not serializable`() {
    val cyclic = CyclicNode()
    cyclic.next = cyclic

    val json = jsonTransfer.mapToJson(mapOf("cyclic" to cyclic))
    assertNull(json)
  }

  @Test
  fun `mapToJson should apply configured sensitive key regex`() {
    jsonTransfer.configureSensitiveKeyRegex(Regex("residentNo", RegexOption.IGNORE_CASE))
    val json =
        jsonTransfer.mapToJson(
            mapOf(
                "residentNo" to "940101-1234567",
                "password" to "1234",
            ),
        )

    val node = objectMapper.readTree(json)
    assertEquals("\"***\"", node["residentNo"].toString())
    assertEquals("\"1234\"", node["password"].toString())
  }

  @Test
  fun `mapToJson parameter regex should override configured regex for that call only`() {
    jsonTransfer.configureSensitiveKeyRegex(Regex("residentNo", RegexOption.IGNORE_CASE))
    val overriddenJson =
        jsonTransfer.mapToJson(
            mapOf("password" to "1234"),
            sensitiveKeyRegex = Regex("password", RegexOption.IGNORE_CASE),
        )
    val defaultConfiguredJson = jsonTransfer.mapToJson(mapOf("password" to "1234"))

    val overriddenNode = objectMapper.readTree(overriddenJson)
    val configuredNode = objectMapper.readTree(defaultConfiguredJson)
    assertEquals("\"***\"", overriddenNode["password"].toString())
    assertEquals("\"1234\"", configuredNode["password"].toString())
  }

  @Test
  fun `instance configuration should be isolated between JsonTransfer instances`() {
    val first = JsonTransfer()
    val second = JsonTransfer()
    first.configureSensitiveKeyRegex(Regex("residentNo", RegexOption.IGNORE_CASE))

    val firstJson = first.mapToJson(mapOf("residentNo" to "940101-1234567", "password" to "1234"))
    val secondJson = second.mapToJson(mapOf("residentNo" to "940101-1234567", "password" to "1234"))

    val firstNode = objectMapper.readTree(firstJson)
    val secondNode = objectMapper.readTree(secondJson)
    assertEquals("\"***\"", firstNode["residentNo"].toString())
    assertEquals("\"1234\"", firstNode["password"].toString())
    assertEquals("\"940101-1234567\"", secondNode["residentNo"].toString())
    assertEquals("\"***\"", secondNode["password"].toString())
  }

  @Test
  fun `constructor sensitive regex should be applied without configure call`() {
    val transfer =
        JsonTransfer(defaultSensitiveKeyRegex = Regex("residentNo", RegexOption.IGNORE_CASE))

    val json = transfer.mapToJson(mapOf("residentNo" to "940101-1234567", "password" to "1234"))
    val node = objectMapper.readTree(json)

    assertEquals("\"***\"", node["residentNo"].toString())
    assertEquals("\"1234\"", node["password"].toString())
  }

  private class CyclicNode {
    var next: CyclicNode? = null
  }
}
