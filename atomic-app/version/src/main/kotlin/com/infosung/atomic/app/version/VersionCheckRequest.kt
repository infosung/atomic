package com.infosung.atomic.app.version

/** Version check request payload resolved from required HTTP headers. */
data class VersionCheckRequest(
    val service: String,
    val platform: String,
    val appVersion: String,
)
