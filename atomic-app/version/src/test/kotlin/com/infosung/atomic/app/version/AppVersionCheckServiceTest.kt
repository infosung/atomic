package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

class AppVersionCheckServiceTest {
  @Test
  fun `checkVersion should resolve targeted repository lookups without loading all policy rows`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 2, minor = 0, patch = 0, requireUpdate = false))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 2, minor = 0, patch = 0, requireUpdate = false))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
            ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(
            listOf(
                version(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeUrl = "https://force.update"),
            ),
        )

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.3",
            ),
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
    verify(repository, times(1))
        .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
            "MY_SERVICE",
            "ANDROID",
        )
    verify(repository, times(1))
        .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
            "MY_SERVICE",
            "ANDROID",
        )
    verify(repository, times(1))
        .findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
            "MY_SERVICE",
            "ANDROID",
            1,
            2,
            3,
        )
    verify(repository, times(1))
        .findRequiredUpdateTargetsHigherThan(
            "MY_SERVICE",
            "ANDROID",
            1,
            2,
            3,
            PageRequest.of(0, 1),
        )
    verify(repository, never())
        .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
            "MY_SERVICE",
            "ANDROID",
        )
    verifyNoMoreInteractions(repository)
  }

  @Test
  fun `checkVersion should use default store url when required target has blank url`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 4, requireUpdate = true, storeUrl = " "))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 4, requireUpdate = true, storeUrl = " "))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
            ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(
            listOf(
                version(main = 1, minor = 2, patch = 4, requireUpdate = true, storeUrl = " "),
            ),
        )

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.3",
            ),
        )

    assertTrue(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `checkVersion should return no required update when higher required policy does not exist`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
            ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(emptyList())

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.3",
            ),
        )

    assertFalse(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `checkVersion should return 404 when no policies exist`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(null)

    val error =
        assertFailsWith<HttpStatusException> {
          service.checkVersion(
              VersionCheckRequest(
                  service = "my_service",
                  platform = "android",
                  appVersion = "1.2.3",
              ),
          )
        }

    assertEquals(404, error.status)
  }

  @Test
  fun `checkVersion should allow unregistered client version and still require update when higher available target exists`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 2, minor = 0, patch = 0, requireUpdate = false))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 2, minor = 0, patch = 0, requireUpdate = false))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
            ),
        )
        .thenReturn(null)
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(
            listOf(
                version(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeUrl = "https://force.update",
                ),
            ),
        )

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.3",
            ),
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertTrue(result.requiredUpdate)
    assertEquals("https://force.update", result.storeUrl)
  }

  @Test
  fun `checkVersion should allow unregistered higher client version without 400`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 5, requireUpdate = false))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 5, requireUpdate = false))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                6,
            ),
        )
        .thenReturn(null)
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                6,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(emptyList())

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.6",
            ),
        )

    assertEquals("1.2.5", result.currentVersion)
    assertEquals("1.2.6", result.userVersion)
    assertFalse(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `checkVersion should prefer latest store available version over newer unavailable version`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            version(main = 2, minor = 1, patch = 0, requireUpdate = false, storeAvailable = false))
    `when`(
            repository
                .findFirstByServiceAndPlatformAndStoreAvailableTrueOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(version(main = 2, minor = 0, patch = 0, requireUpdate = false))
    `when`(
            repository.findFirstByServiceAndPlatformAndMainVersionAndMinorVersionAndPatchNumber(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
            ),
        )
        .thenReturn(version(main = 1, minor = 2, patch = 3, requireUpdate = false))
    `when`(
            repository.findRequiredUpdateTargetsHigherThan(
                "MY_SERVICE",
                "ANDROID",
                1,
                2,
                3,
                PageRequest.of(0, 1),
            ),
        )
        .thenReturn(emptyList())

    val result =
        service.checkVersion(
            VersionCheckRequest(
                service = "my_service",
                platform = "android",
                appVersion = "1.2.3",
            ),
        )

    assertEquals("2.0.0", result.currentVersion)
    assertEquals("1.2.3", result.userVersion)
    assertFalse(result.requiredUpdate)
  }

  @Test
  fun `checkVersion should return 400 when version format is invalid`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")

    val error =
        assertFailsWith<HttpStatusException> {
          service.checkVersion(
              VersionCheckRequest(
                  service = "my_service",
                  platform = "android",
                  appVersion = "1.2",
              ),
          )
        }

    assertEquals(400, error.status)
  }

  private fun version(
      main: Int,
      minor: Int,
      patch: Int,
      requireUpdate: Boolean,
      storeUrl: String? = null,
      storeAvailable: Boolean = true,
  ): ServiceVersionEntity {
    return ServiceVersionEntity(
            mainVersion = main,
            minorVersion = minor,
            patchNumber = patch,
            requireUpdate = requireUpdate,
            service = "MY_SERVICE",
            platform = "ANDROID",
            storeUrl = storeUrl,
        )
        .also { it.storeAvailable = storeAvailable }
  }
}
