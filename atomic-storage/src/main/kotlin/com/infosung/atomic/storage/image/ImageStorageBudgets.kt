package com.infosung.atomic.storage.image

import com.infosung.atomic.contract.log.LogStringPreview

internal object ImageStorageBudgets {
  const val MAX_OBJECT_KEY_LENGTH: Int = 512
  const val MAX_PUBLIC_URL_LENGTH: Int = 2048
  const val MAX_LOG_PREVIEW_LENGTH: Int = LogStringPreview.DEFAULT_MAX_LENGTH
  const val MAX_FAILURE_REASON_LENGTH: Int = 160

  fun summarizeForLog(value: String?): String? = LogStringPreview.summarize(value)

  fun summarizeFailureReason(value: String): String = clip(value, MAX_FAILURE_REASON_LENGTH)

  private fun clip(value: String, maxLength: Int): String {
    if (value.length <= maxLength) {
      return value
    }
    if (maxLength <= 3) {
      return value.take(maxLength)
    }
    return value.take(maxLength - 3) + "..."
  }
}
