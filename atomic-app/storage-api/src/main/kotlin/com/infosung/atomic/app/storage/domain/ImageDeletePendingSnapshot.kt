package com.infosung.atomic.app.storage.domain

import java.time.LocalDateTime

data class ImageDeletePendingSnapshot(
    val pendingCount: Long,
    val oldestPendingCreatedAt: LocalDateTime?,
)
