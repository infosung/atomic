package com.infosung.atomic.spring.web.json

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

class JsonTransfer(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val baseSensitiveKeyRegex: Regex =
        Regex(
            pattern =
                "(password|passwd|pwd|secret|authorization|api[-_]?key|token|access[-_]?token|refresh[-_]?token)",
            option = RegexOption.IGNORE_CASE,
        ),
    private val defaultSensitiveKeyRegex: Regex? = null,
) {
  private val log = LoggerFactory.getLogger(JsonTransfer::class.java)
  private val maskedValue = "***"
  @Volatile private var sensitiveKeyRegex: Regex? = defaultSensitiveKeyRegex

  fun configureSensitiveKeyRegex(regex: Regex?) {
    sensitiveKeyRegex = regex
  }

  fun configureSensitiveKeyRegex(
      pattern: String,
      option: RegexOption = RegexOption.IGNORE_CASE,
  ) {
    sensitiveKeyRegex = Regex(pattern = pattern, option = option)
  }

  fun resetSensitiveKeyRegex() {
    sensitiveKeyRegex = defaultSensitiveKeyRegex
  }

  fun mapToJson(
      map: Map<*, *>?,
      sensitiveKeyRegex: Regex? = null,
  ): String? {
    if (map.isNullOrEmpty()) return null
    return toSafeJson(map, resolveSensitiveKeyRegex(sensitiveKeyRegex))
  }

  fun listToJson(
      list: List<*>?,
      sensitiveKeyRegex: Regex? = null,
  ): String? {
    if (list.isNullOrEmpty()) return null
    return toSafeJson(list, resolveSensitiveKeyRegex(sensitiveKeyRegex))
  }

  fun arrayToJson(
      array: Array<*>?,
      sensitiveKeyRegex: Regex? = null,
  ): String? {
    if (array.isNullOrEmpty()) return null
    return toSafeJson(array, resolveSensitiveKeyRegex(sensitiveKeyRegex))
  }

  private fun resolveSensitiveKeyRegex(overrideRegex: Regex?): Regex =
      overrideRegex ?: sensitiveKeyRegex ?: baseSensitiveKeyRegex

  private fun toSafeJson(
      value: Any,
      sensitiveKeyRegex: Regex,
  ): String? =
      try {
        val normalized = objectMapper.convertValue(value, Any::class.java)
        objectMapper.writeValueAsString(maskSensitive(normalized, sensitiveKeyRegex))
      } catch (e: Exception) {
        log.warn("Failed to serialize log payload. type={}", value::class.java.name, e)
        null
      }

  private fun maskSensitive(
      data: Any?,
      sensitiveKeyRegex: Regex,
  ): Any? =
      when (data) {
        is Map<*, *> -> {
          data.entries.associate { (key, value) ->
            val keyAsString = key?.toString() ?: ""
            keyAsString to
                if (isSensitiveKey(keyAsString, sensitiveKeyRegex)) {
                  maskedValue
                } else {
                  maskSensitive(value, sensitiveKeyRegex)
                }
          }
        }

        is Iterable<*> -> data.map { maskSensitive(it, sensitiveKeyRegex) }
        is Array<*> -> data.map { maskSensitive(it, sensitiveKeyRegex) }
        else -> data
      }

  private fun isSensitiveKey(
      key: String,
      sensitiveKeyRegex: Regex,
  ): Boolean = sensitiveKeyRegex.containsMatchIn(key)
}
