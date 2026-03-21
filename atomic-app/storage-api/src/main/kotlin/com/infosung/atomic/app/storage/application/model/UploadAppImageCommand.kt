package com.infosung.atomic.app.storage.application.model

data class UploadAppImageCommand(
    val serviceName: String,
    val storageService: String,
    val quality: Double,
    val uploaderId: String? = null,
    val thumbnailEnabled: Boolean,
    val uploadSource: UploadImageSource,
)
