package com.infosung.atomic.spring.web.log

abstract class ServiceLog(
    open val traceId: String,
    open val logTime: Long,
)

data class ServiceApiRequestLog(
    override val traceId: String,
    override val logTime: Long,
    val customLang: String? = null,
    val acceptLanguage: String? = null,
    val httpMethod: String,
    val endPoint: String,
    val userId: String? = null,
    val deviceId: String? = null,
    val clientIp: String? = null,
    val query: String? = null,
    val body: String? = null,
) :
    ServiceLog(
        traceId = traceId,
        logTime = logTime,
    )

data class ServiceApiResponseLog(
    override val traceId: String,
    override val logTime: Long,
    val httpMethod: String,
    val executeTime: Long,
    val status: Int,
    val endPoint: String,
    val userId: String? = null,
    val deviceId: String? = null,
    val clientIp: String? = null,
) :
    ServiceLog(
        traceId = traceId,
        logTime = logTime,
    )

interface LogSaver {
  fun saveAll(logs: List<ServiceLog>)
}
