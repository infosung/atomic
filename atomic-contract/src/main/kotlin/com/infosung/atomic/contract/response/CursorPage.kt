package com.infosung.atomic.contract.response

data class CursorPage<T>(
    val list: List<T> = listOf(),
    val hasNext: Boolean = false,
    val size: Int = 10,
    val cursor: String? = null,
)
