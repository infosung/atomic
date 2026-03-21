package com.infosung.atomic.app.storage.application.port.`in`

import com.infosung.atomic.app.storage.domain.ImageDeleteRecoveryResult

interface RecoverDeletePendingImagesUseCase {
  fun recoverDeletePendingImages(limit: Int = 50): ImageDeleteRecoveryResult
}
