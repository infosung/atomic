package com.infosung.atomic.app.version.domain

internal data class VersionCheckDecision(
    val currentVersion: String,
    val userVersion: String,
    val requiredUpdate: Boolean,
    val storeUrl: String,
)
