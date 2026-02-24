package com.infosung.atomic.contract.response

data class BaseResponse<T>(
    val code: String,
    val message: String,
    val data: T? = null,
) {
  companion object {
    fun <T> ok(data: T? = null): BaseResponse<T> =
        BaseResponse(
            code = "OK",
            message = "Success",
            data = data,
        )

    fun <T> error(e: Exception): BaseResponse<T> =
        BaseResponse(
            code = e::class.java.simpleName,
            message = e.message ?: e::class.java.simpleName,
        )
  }
}
