package com.infosung.atomic.app.version.domain

internal data class VersionPolicy(
    val service: String,
    val platform: String,
    val version: SemanticVersion,
    val requireUpdate: Boolean,
    val storeAvailable: Boolean,
    val storeUrl: String?,
)
