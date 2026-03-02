package com.infosung.atomic.contract.response

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
    /**
     * Creates success response with optional payload.
     */
    fun <T> ok(data: T? = null): BaseResponse<T> =
        BaseResponse(
            code = "OK",
            message = "Success",
            data = data,
        )

    /**
     * Creates error response from exception type and message.
     */
    fun <T> error(e: Exception): BaseResponse<T> =
        BaseResponse(
            code = e::class.java.simpleName,
            message = e.message ?: e::class.java.simpleName,
        )
  }
}
