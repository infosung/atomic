package com.infosung.atomic.app.storage.application.model

data class StoredImageObject(
    val bucket: String,
    val storageType: String,
    val fileName: String,
    val thumbnailFileName: String?,
    val url: String,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?,
    val fileSize: Long,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val thumbnailFileSize: Long?,
)
