package com.infosung.atomic.contract.log

object LogStringPreview {
  const val DEFAULT_MAX_LENGTH: Int = 96

  fun summarize(
      value: String?,
      maxLength: Int = DEFAULT_MAX_LENGTH,
  ): String? = value?.let { clip(it, maxLength) }

  private fun clip(
      value: String,
      maxLength: Int,
  ): String {
    if (value.length <= maxLength) {
      return value
    }
    if (maxLength <= 3) {
      return value.take(maxLength)
    }
    return value.take(maxLength - 3) + "..."
  }
}
