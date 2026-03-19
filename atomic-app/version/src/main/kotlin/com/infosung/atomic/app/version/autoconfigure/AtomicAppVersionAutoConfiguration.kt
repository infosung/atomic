package com.infosung.atomic.app.version.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Import

/** Stable umbrella auto-configuration entrypoint for the app-version module. */
@AutoConfiguration
@Import(
    AtomicAppVersionPersistenceAutoConfiguration::class,
    AtomicAppVersionCoreAutoConfiguration::class,
    AtomicAppVersionWebAutoConfiguration::class,
)
class AtomicAppVersionAutoConfiguration
