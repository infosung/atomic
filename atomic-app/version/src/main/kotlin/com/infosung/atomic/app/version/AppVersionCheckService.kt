package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.out.persistence.JpaLoadVersionPolicyAdapter
import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.app.version.application.port.`in`.CheckAppVersionUseCase
import com.infosung.atomic.app.version.application.service.CheckAppVersionService
import com.infosung.atomic.contract.exception.HttpStatusException
import java.lang.reflect.Proxy
import org.slf4j.LoggerFactory

/**
 * Stable facade for the app-version API.
 *
 * The HTTP/property/schema contract is exposed through this type, while the internal decision flow
 * is delegated to the hexagonal use-case layer. Host applications should keep treating this facade
 * bean as the supported override point for `0.0.4`.
 */
class AppVersionCheckService(
    private val serviceVersionRepository: ServiceVersionRepository,
    private val defaultStoreUrl: String,
) {
  private val log = LoggerFactory.getLogger(this::class.java)
  private var injectedCheckAppVersionUseCase: CheckAppVersionUseCase? = null
  private val defaultCheckAppVersionUseCase: CheckAppVersionUseCase by lazy {
    defaultUseCase(
        serviceVersionRepository = serviceVersionRepository,
        defaultStoreUrl = defaultStoreUrl,
    )
  }
  private val checkAppVersionUseCase: CheckAppVersionUseCase
    get() = injectedCheckAppVersionUseCase ?: defaultCheckAppVersionUseCase

  init {
    log.debug(
        "Configured app version facade with default persistence-backed use-case: repositoryType={}, defaultStoreUrlLength={}",
        serviceVersionRepository::class.java.name,
        defaultStoreUrl.length,
    )
  }

  internal constructor(
      defaultStoreUrl: String,
      checkAppVersionUseCase: CheckAppVersionUseCase,
  ) : this(unsupportedServiceVersionRepository(), defaultStoreUrl) {
    this.injectedCheckAppVersionUseCase = checkAppVersionUseCase
    log.debug(
        "Configured app version facade with injected use-case composition and no repository fallback: defaultStoreUrlLength={}, useCaseType={}",
        defaultStoreUrl.length,
        checkAppVersionUseCase::class.java.name,
    )
  }

  internal constructor(
      serviceVersionRepository: ServiceVersionRepository,
      defaultStoreUrl: String,
      checkAppVersionUseCase: CheckAppVersionUseCase,
  ) : this(serviceVersionRepository, defaultStoreUrl) {
    this.injectedCheckAppVersionUseCase = checkAppVersionUseCase
    log.debug(
        "Configured app version facade with injected use-case composition: repositoryType={}, defaultStoreUrlLength={}, useCaseType={}",
        serviceVersionRepository::class.java.name,
        defaultStoreUrl.length,
        checkAppVersionUseCase::class.java.name,
    )
  }

  /**
   * Checks version policy and returns update requirement.
   *
   * @throws HttpStatusException 400 when request format is invalid.
   * @throws HttpStatusException 404 when no version policy exists.
   */
  fun checkVersion(request: VersionCheckRequest): VersionCheckResult {
    log.debug(
        "Delegating app version facade to use-case: service={}, platform={}, appVersion={}",
        request.service,
        request.platform,
        request.appVersion,
    )
    val decision =
        try {
          checkAppVersionUseCase.check(
              service = request.service,
              platform = request.platform,
              appVersion = request.appVersion,
          )
        } catch (e: InvalidAppVersionException) {
          log.warn(
              "Translating application invalid-version error at facade boundary: service={}, platform={}, appVersion={}, message={}",
              request.service,
              request.platform,
              request.appVersion,
              e.message,
          )
          throw HttpStatusException(
              status = 400,
              message = e.message ?: "Invalid app version.",
              cause = e,
          )
        } catch (e: VersionPolicyNotFoundException) {
          log.warn(
              "Translating application policy-not-found error at facade boundary: service={}, platform={}, appVersion={}, message={}",
              request.service,
              request.platform,
              request.appVersion,
              e.message,
          )
          throw HttpStatusException(
              status = 404,
              message = e.message ?: "No version policy found.",
              cause = e,
          )
        }
    log.debug(
        "App version facade completed: service={}, platform={}, currentVersion={}, requiredUpdate={}",
        request.service,
        request.platform,
        decision.currentVersion,
        decision.requiredUpdate,
    )
    return VersionCheckResult(
        currentVersion = decision.currentVersion,
        userVersion = decision.userVersion,
        requiredUpdate = decision.requiredUpdate,
        storeUrl = decision.storeUrl,
    )
  }

  private companion object {
    fun unsupportedServiceVersionRepository(): ServiceVersionRepository {
      return Proxy.newProxyInstance(
          ServiceVersionRepository::class.java.classLoader,
          arrayOf(ServiceVersionRepository::class.java),
      ) { _, method, _ ->
        when (method.name) {
          "toString" -> "UnsupportedServiceVersionRepository"
          "hashCode" -> System.identityHashCode(method.declaringClass)
          "equals" -> false
          else ->
              throw UnsupportedOperationException(
                  "Repository-backed fallback is unavailable for this app version facade path.",
              )
        }
      } as ServiceVersionRepository
    }

    fun defaultUseCase(
        serviceVersionRepository: ServiceVersionRepository,
        defaultStoreUrl: String,
    ): CheckAppVersionUseCase {
      return CheckAppVersionService(
          loadVersionPolicyPort = JpaLoadVersionPolicyAdapter(serviceVersionRepository),
          defaultStoreUrl = defaultStoreUrl,
      )
    }
  }
}
