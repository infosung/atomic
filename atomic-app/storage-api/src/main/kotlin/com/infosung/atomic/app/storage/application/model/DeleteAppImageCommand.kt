package com.infosung.atomic.app.storage.application.model

data class DeleteAppImageCommand(
    val serviceName: String,
    val storageService: String,
    val imageId: String,
    val uploaderId: String? = null,
)
