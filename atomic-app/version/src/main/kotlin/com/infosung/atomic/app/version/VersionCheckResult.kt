package com.infosung.atomic.app.version

/** Version check response payload. */
data class VersionCheckResult(
    val currentVersion: String,
    val userVersion: String,
    val requiredUpdate: Boolean,
    val storeUrl: String,
)
