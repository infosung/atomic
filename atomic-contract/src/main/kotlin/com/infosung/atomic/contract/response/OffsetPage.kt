package com.infosung.atomic.contract.response

data class OffsetPage<T>(
    val list: List<T> = listOf(),
    val totalSize: Long = 0,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val size: Int = 10,
) {
  companion object {
    fun <T> build(
        list: List<T>,
        totalSize: Long,
        currentPage: Int = 0,
        size: Int = 10,
    ): OffsetPage<T> {
      val currentSize = currentPage.toLong() * size + list.size

      return OffsetPage(
          list = list,
          totalSize = totalSize,
          currentPage = currentPage,
          size = size,
          hasNext = totalSize > currentSize,
      )
    }

    fun <T> empty(
        currentPage: Int = 0,
        size: Int = 10,
    ): OffsetPage<T> =
        OffsetPage(
            list = emptyList(),
            totalSize = 0,
            currentPage = currentPage,
            size = size,
        )
  }
}
