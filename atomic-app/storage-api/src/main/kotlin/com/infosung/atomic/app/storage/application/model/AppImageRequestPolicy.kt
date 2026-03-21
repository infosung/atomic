package com.infosung.atomic.app.storage.application.model

data class AppImageRequestPolicy(
    val minQuality: Double,
    val maxQuality: Double,
    val uploaderParameterEnabled: Boolean,
    val uploaderParameterName: String,
)
