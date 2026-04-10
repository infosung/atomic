package com.infosung.atomic.event.log.domain

/** Validation and masking policy shared by adapters. */
data class EventLogPolicy(
    val supportedSchemaVersion: Int = 1,
    val maxBatchSize: Int = 500,
    val maxIdentifierLength: Int = 128,
    val maxEventNameLength: Int = 120,
    val maxPayloadEntries: Int = 128,
    val maxTextLength: Int = 8_192,
    val allowedEventNamePattern: Regex = Regex("[a-z0-9._-]+"),
    val sensitiveKeyPattern: Regex =
        Regex(
            pattern =
                "(password|passwd|pwd|secret|authorization|api[-_]?key|token|access[-_]?token|refresh[-_]?token)",
            option = RegexOption.IGNORE_CASE,
        ),
) {
  init {
    require(supportedSchemaVersion > 0) { "supportedSchemaVersion must be greater than zero." }
    require(maxBatchSize > 0) { "maxBatchSize must be greater than zero." }
    require(maxIdentifierLength > 0) { "maxIdentifierLength must be greater than zero." }
    require(maxEventNameLength > 0) { "maxEventNameLength must be greater than zero." }
    require(maxPayloadEntries >= 0) { "maxPayloadEntries must be zero or greater." }
    require(maxTextLength > 0) { "maxTextLength must be greater than zero." }
  }
}
