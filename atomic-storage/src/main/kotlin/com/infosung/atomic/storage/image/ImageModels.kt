package com.infosung.atomic.storage.image

/**
 * Image metadata measured from a local file.
 *
 * @property width Pixel width.
 * @property height Pixel height.
 * @property size File size in bytes.
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val size: Long,
)

/**
 * Optional file info used for thumbnail payloads.
 *
 * @property fileName Stored object key of the file.
 * @property width Pixel width.
 * @property height Pixel height.
 * @property size File size in bytes.
 */
data class ImageFileInfo(
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
)

/**
 * Result returned by [ImageService.uploadImage].
 *
 * @property storageType Logical storage profile key used for the upload.
 * @property bucket Physical bucket/container name.
 * @property storageObjectKey Original object key without optional bucket prefix.
 * @property storageThumbnailObjectKey Thumbnail object key without optional bucket prefix.
 * @property fileName Display/object name (may include bucket prefix depending on profile).
 * @property thumbnailFileName Thumbnail display/object name.
 * @property url Public URL for the original object.
 * @property thumbnailUrl Public URL for the thumbnail object.
 * @property width Original width.
 * @property height Original height.
 * @property fileSize Original file size in bytes.
 * @property thumbnailWidth Thumbnail width.
 * @property thumbnailHeight Thumbnail height.
 * @property thumbnailFileSize Thumbnail file size in bytes.
 * @property thumbnailUploadFailed Whether thumbnail generation/upload failed after original upload.
 * @property thumbnailFailureReason Failure summary when thumbnail step fails.
 */
data class ImageUploadResult(
    val storageType: String,
    val bucket: String,
    val storageObjectKey: String,
    val storageThumbnailObjectKey: String? = null,
    val fileName: String,
    val thumbnailFileName: String? = null,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fileSize: Long,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val thumbnailFileSize: Long? = null,
    val thumbnailUploadFailed: Boolean = false,
    val thumbnailFailureReason: String? = null,
)
