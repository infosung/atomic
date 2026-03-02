package com.infosung.atomic.contract.response

/**
 * Offset-based pagination payload.
 *
 * @property list Current page items.
 * @property totalSize Total number of rows.
 * @property hasNext Whether more rows exist after current page.
 * @property currentPage Zero-based page index.
 * @property size Requested page size.
 */
data class OffsetPage<T>(
    val list: List<T> = listOf(),
    val totalSize: Long = 0,
    val hasNext: Boolean = false,
    val currentPage: Int = 0,
    val size: Int = 10,
) {
  companion object {
    /** Builds a page object and calculates [OffsetPage.hasNext]. */
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

    /** Creates an empty page response. */
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
