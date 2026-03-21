package com.infosung.atomic.app.storage.application.port.out

import com.infosung.atomic.app.storage.domain.ImageDeletePendingSnapshot
import com.infosung.atomic.app.storage.domain.StoredImage
import java.time.LocalDateTime
import java.util.UUID

interface ImageMetadataPort {
  fun findByIdOrThrow(imageId: UUID, rawImageId: String): StoredImage

  fun save(image: StoredImage): StoredImage

  fun markDeletePending(image: StoredImage): StoredImage

  fun purgeDeletePending(image: StoredImage)

  fun inspectDeletePendingImages(): ImageDeletePendingSnapshot

  fun claimDeletePending(
      limit: Int,
      claimToken: String,
      claimedAt: LocalDateTime,
  ): List<StoredImage>

  fun releaseDeleteRecoveryClaim(
      imageId: UUID,
      claimToken: String,
  )
}
