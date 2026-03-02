package com.infosung.atomic.spring.web.log

import com.infosung.atomic.contract.header.TraceIdGenerator
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.header.toHeaderDto
import com.infosung.atomic.spring.web.json.JsonTransfer
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory

/** Marker annotation to exclude controller methods from API logging. */
annotation class DoNotApiLog

/** Aspect that captures inbound API request logs for controller methods. */
abstract class ApiLogAspect(
    protected val jsonTransfer: JsonTransfer,
    protected val timeProvider: TimeProvider = TimeProvider(),
    protected val traceIdGenerator: TraceIdGenerator = TraceIdGenerator(),
) {
  private val log = LoggerFactory.getLogger(ApiLogAspect::class.java)

  /** Persists request-side log payload. */
  abstract fun logging(log: ServiceLog)

  /** Resolves user id from security context when available. */
  abstract fun getUserId(): Any?

  /** Around advice for POST/PUT/PATCH handlers. */
  @Around(
      "(@annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.PutMapping) || @annotation(org.springframework.web.bind.annotation.PatchMapping)) && !@annotation(com.infosung.atomic.spring.web.log.DoNotApiLog)")
  open fun postPutPatchLog(joinPoint: ProceedingJoinPoint): Any? {
    log.debug("API log aspect invoked for write method")
    val signature = joinPoint.signature as MethodSignature

    val request =
        currentRequest(joinPoint)
            ?: run {
              log.trace("No HttpServletRequest found for write method")
              return joinPoint.proceed()
            }
    val requestBody =
        if (joinPoint.args.isNotEmpty())
            getRequestBody(
                argsArray = joinPoint.args,
                signature = signature,
            )
        else null

    val headerDto = request.toHeaderDto(traceIdGenerator = traceIdGenerator)
    log.trace("Request header dto={}", headerDto)
    log.trace("Request body payload={}", requestBody)

    val startTime = timeProvider.nowMillis()
    val userId = getUserId()
    val requestLog =
        ServiceApiRequestLog(
            traceId = headerDto.traceId,
            logTime = startTime,
            httpMethod = request.method,
            endPoint = request.requestURI,
            userId = userId?.toString(),
            deviceId = headerDto.deviceId,
            clientIp = headerDto.clientIp,
            customLang = headerDto.customLang,
            acceptLanguage = headerDto.acceptLanguage,
            query = jsonTransfer.mapToJson(request.parameterMap),
            body = jsonTransfer.mapToJson(requestBody),
        )
    ApiLogContext.set(request, requestLog)
    logging(requestLog)
    return joinPoint.proceed()
  }

  /** Around advice for GET/DELETE handlers. */
  @Around(
      "(@annotation(org.springframework.web.bind.annotation.GetMapping) || @annotation(org.springframework.web.bind.annotation.DeleteMapping)) && !@annotation(com.infosung.atomic.spring.web.log.DoNotApiLog)")
  open fun getDeleteLog(joinPoint: ProceedingJoinPoint): Any? {
    log.debug("API log aspect invoked for read method")
    val requestBody = null
    val request =
        currentRequest(joinPoint)
            ?: run {
              log.trace("No HttpServletRequest found for read method")
              return joinPoint.proceed()
            }
    val headerDto = request.toHeaderDto(traceIdGenerator = traceIdGenerator)
    log.trace("Request header dto={}", headerDto)

    val startTime = timeProvider.nowMillis()
    val userId = getUserId()
    val requestLog =
        ServiceApiRequestLog(
            traceId = headerDto.traceId,
            logTime = startTime,
            httpMethod = request.method,
            endPoint = request.requestURI,
            userId = userId?.toString(),
            deviceId = headerDto.deviceId,
            clientIp = headerDto.clientIp,
            customLang = headerDto.customLang,
            acceptLanguage = headerDto.acceptLanguage,
            query = jsonTransfer.mapToJson(request.parameterMap),
            body = requestBody,
        )
    ApiLogContext.set(request, requestLog)
    logging(requestLog)

    return joinPoint.proceed()
  }

  private fun getRequestBody(
      argsArray: Array<Any?>,
      signature: MethodSignature,
  ): MutableMap<String, Any?>? {
    val requestBody = mutableMapOf<String, Any?>()
    for ((index, args) in argsArray.withIndex()) {
      val parameterName = signature.parameterNames[index]
      if (parameterName == "request" || parameterName == "response") continue
      if (args is ServletRequest || args is ServletResponse) continue
      requestBody[parameterName] = args
    }
    return requestBody.ifEmpty { null }
  }

  private fun currentRequest(joinPoint: ProceedingJoinPoint): HttpServletRequest? {
    joinPoint.args
        .firstOrNull { it is HttpServletRequest }
        ?.let {
          log.trace("Resolved HttpServletRequest from joinPoint arguments")
          return it as HttpServletRequest
        }
    log.trace("Falling back to request context resolver")
    return resolveRequestFromContext()
  }

  protected open fun resolveRequestFromContext(): HttpServletRequest? = null
}
