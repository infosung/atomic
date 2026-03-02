package com.infosung.atomic.storage

/**
 * Read/write profile information for a storage target.
 *
 * @property bucket Physical bucket/container name.
 * @property cdn Public base URL used to build object URLs returned by this module.
 * @property prependBucketOnObjectKey If true, returned/displayed object names include `bucket/`
 *   prefix.
 */
data class StorageProfile(
    val bucket: String,
    val cdn: String,
    val prependBucketOnObjectKey: Boolean = false,
)
