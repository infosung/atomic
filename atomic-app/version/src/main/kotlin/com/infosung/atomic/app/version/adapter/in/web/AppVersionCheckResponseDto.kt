package com.infosung.atomic.app.version.adapter.`in`.web

data class AppVersionCheckResponseDto(
    val currentVersion: String,
    val userVersion: String,
    val requiredUpdate: Boolean,
    val storeUrl: String,
)
