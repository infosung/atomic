package com.infosung.atomic.app.storage.application.port.`in`

import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot

interface InspectDeletePendingImagesUseCase {
  fun inspectDeletePendingImages(): ImageDeletePendingSnapshot
}
