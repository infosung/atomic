package com.infosung.atomic.app.version.application.service

import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.app.version.application.port.out.LoadVersionPolicyPort
import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckAppVersionServiceTest {
  @Test
  fun `check should prefer latest store-available version for currentVersion`() {
    val port =
        FakeLoadVersionPolicyPort(
            latestRegistered =
                versionPolicy(main = 2, minor = 1, patch = 0, storeAvailable = false),
            latestStoreAvailable =
                versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
        )
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = port,
            defaultStoreUrl = "https://default.store",
        )

    val result =
        service.check(
            service = "my_service",
            platform = "android",
            appVersion = "1.2.3",
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertFalse(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `check should fallback to latest registered version when no store-available row exists`() {
    val latestRegistered =
        versionPolicy(main = 2, minor = 1, patch = 0, requireUpdate = false, storeAvailable = false)
    val port =
        FakeLoadVersionPolicyPort(
            latestRegistered = latestRegistered,
            latestStoreAvailable = null,
        )
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = port,
            defaultStoreUrl = "https://default.store",
        )

    val result =
        service.check(
            service = "my_service",
            platform = "android",
            appVersion = "1.9.9",
        )

    assertEquals("2.1.0", result.currentVersion)
    assertEquals("1.9.9", result.userVersion)
  }

  @Test
  fun `check should use required-update target store url when available`() {
    val port =
        FakeLoadVersionPolicyPort(
            latestRegistered = versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
            latestStoreAvailable =
                versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
            requiredUpdateTarget =
                versionPolicy(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeAvailable = true,
                    storeUrl = "https://force.update",
                ),
        )
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = port,
            defaultStoreUrl = "https://default.store",
        )

    val result =
        service.check(
            service = "my_service",
            platform = "android",
            appVersion = "1.2.3",
        )

    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
  }

  @Test
  fun `check should use default store url when required-update target store url is blank`() {
    val port =
        FakeLoadVersionPolicyPort(
            latestRegistered = versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
            latestStoreAvailable =
                versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
            requiredUpdateTarget =
                versionPolicy(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeAvailable = true,
                    storeUrl = " ",
                ),
        )
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = port,
            defaultStoreUrl = "https://default.store",
        )

    val result =
        service.check(
            service = "my_service",
            platform = "android",
            appVersion = "1.2.3",
        )

    assertTrue(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `check should allow unregistered client version`() {
    val port =
        FakeLoadVersionPolicyPort(
            latestRegistered = versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
            latestStoreAvailable =
                versionPolicy(main = 2, minor = 0, patch = 0, storeAvailable = true),
        )
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = port,
            defaultStoreUrl = "https://default.store",
        )

    val result =
        service.check(
            service = "my_service",
            platform = "android",
            appVersion = "1.2.3",
        )

    assertEquals("1.2.3", result.userVersion)
    assertFalse(result.requiredUpdate)
  }

  @Test
  fun `check should reject invalid semantic version`() {
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = FakeLoadVersionPolicyPort(),
            defaultStoreUrl = "https://default.store",
        )

    val error =
        assertFailsWith<InvalidAppVersionException> {
          service.check(
              service = "my_service",
              platform = "android",
              appVersion = "1.2",
          )
        }

    assertEquals("Version must be semantic format: x.y.z", error.message)
  }

  @Test
  fun `check should reject missing version policies`() {
    val service =
        CheckAppVersionService(
            loadVersionPolicyPort = FakeLoadVersionPolicyPort(),
            defaultStoreUrl = "https://default.store",
        )

    val error =
        assertFailsWith<VersionPolicyNotFoundException> {
          service.check(
              service = "my_service",
              platform = "android",
              appVersion = "1.2.3",
          )
        }

    assertEquals(
        "No service version policy found for service=MY_SERVICE, platform=ANDROID",
        error.message,
    )
  }

  private fun versionPolicy(
      main: Int,
      minor: Int,
      patch: Int,
      requireUpdate: Boolean = false,
      storeAvailable: Boolean = true,
      storeUrl: String? = null,
  ): VersionPolicy {
    return VersionPolicy(
        service = "MY_SERVICE",
        platform = "ANDROID",
        version = SemanticVersion(major = main, minor = minor, patch = patch),
        requireUpdate = requireUpdate,
        storeAvailable = storeAvailable,
        storeUrl = storeUrl,
    )
  }

  private class FakeLoadVersionPolicyPort(
      private val latestRegistered: VersionPolicy? = null,
      private val latestStoreAvailable: VersionPolicy? = null,
      private val requiredUpdateTarget: VersionPolicy? = null,
  ) : LoadVersionPolicyPort {
    override fun loadLatestRegistered(service: String, platform: String): VersionPolicy? =
        latestRegistered

    override fun loadLatestStoreAvailable(service: String, platform: String): VersionPolicy? =
        latestStoreAvailable

    override fun loadRequiredUpdateTargetAbove(
        service: String,
        platform: String,
        version: SemanticVersion,
    ): VersionPolicy? = requiredUpdateTarget
  }
}
