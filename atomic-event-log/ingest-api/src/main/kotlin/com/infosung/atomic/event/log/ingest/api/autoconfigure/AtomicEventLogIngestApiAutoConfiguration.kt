package com.infosung.atomic.event.log.ingest.api.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

/** Umbrella auto-configuration for the official event-log ingest API module. */
@AutoConfiguration(
    afterName = ["com.infosung.atomic.spring.web.autoconfigure.AtomicSpringWebAutoConfiguration"])
@ConditionalOnClass(name = ["org.springframework.web.bind.annotation.RestController"])
@ConditionalOnProperty(
    prefix = "atomic.event.log.ingest",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(AtomicEventLogIngestApiProperties::class)
@Import(
    AtomicEventLogIngestApiCoreAutoConfiguration::class,
    AtomicEventLogIngestApiWebAutoConfiguration::class,
)
class AtomicEventLogIngestApiAutoConfiguration
