package com.infosung.atomic.contract.response

import com.infosung.atomic.contract.exception.HttpStatusException

/**
 * Standard API response envelope.
 *
 * @property code Business/result code.
 * @property message Human-readable summary.
 * @property data Optional payload.
 */
data class BaseResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null,
) {
  companion object {
    /** Creates success response with optional payload. */
    fun <T> ok(data: T? = null): BaseResponse<T> =
        BaseResponse(
            code = "OK",
            message = "Success",
            data = data,
        )

    /** Creates error response from exception type and message. */
    fun <T> error(e: Exception): BaseResponse<T> =
        BaseResponse(
            code = resolveCode(e),
            message = e.message ?: e::class.java.simpleName,
        )

    private fun resolveCode(e: Exception): String {
      return (e as? HttpStatusException)?.code?.takeIf { it.isNotBlank() }
          ?: e::class.java.simpleName
    }
  }
}
