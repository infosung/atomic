package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppVersionCheckServiceTest {
  @Test
  fun `checkVersion should return required update when higher required policy exists`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            listOf(
                version(main = 2, minor = 0, patch = 0, requireUpdate = false),
                version(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeUrl = "https://force.update"),
                version(main = 1, minor = 2, patch = 3, requireUpdate = false),
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
  fun `checkVersion should use default store url when required target has blank url`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            listOf(
                version(main = 1, minor = 2, patch = 4, requireUpdate = true, storeUrl = " "),
                version(main = 1, minor = 2, patch = 3, requireUpdate = false),
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
                .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            listOf(
                version(main = 1, minor = 2, patch = 3, requireUpdate = false),
                version(main = 1, minor = 2, patch = 2, requireUpdate = false),
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

    assertFalse(result.requiredUpdate)
    assertEquals("https://default.store", result.storeUrl)
  }

  @Test
  fun `checkVersion should return 404 when no policies exist`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(emptyList())

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
  fun `checkVersion should return 400 when client version is not registered`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val service = AppVersionCheckService(repository, defaultStoreUrl = "https://default.store")
    `when`(
            repository
                .findAllByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            listOf(
                version(main = 1, minor = 2, patch = 4, requireUpdate = false),
                version(main = 1, minor = 2, patch = 5, requireUpdate = false),
            ),
        )

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

    assertEquals(400, error.status)
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
  }
}
