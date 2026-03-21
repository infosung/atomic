package com.infosung.atomic.app.storage.domain

import java.time.LocalDateTime

data class ImageDeleteRecoveryResult(
    val scannedCount: Int,
    val recoveredCount: Int,
    val failedCount: Int,
    val remainingPendingCount: Long,
    val oldestPendingCreatedAt: LocalDateTime?,
)
