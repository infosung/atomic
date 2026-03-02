package com.infosung.atomic.contract.response

/**
 * Cursor-based pagination payload.
 *
 * @property list Current page items.
 * @property hasNext Whether next cursor page exists.
 * @property size Requested page size.
 * @property cursor Next cursor value.
 */
data class CursorPage<T>(
    val list: List<T> = listOf(),
    val hasNext: Boolean = false,
    val size: Int = 10,
    val cursor: String? = null,
)
