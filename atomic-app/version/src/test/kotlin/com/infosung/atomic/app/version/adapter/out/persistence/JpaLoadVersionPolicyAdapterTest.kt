package com.infosung.atomic.app.version.adapter.out.persistence

import com.infosung.atomic.app.version.ServiceVersionEntity
import com.infosung.atomic.app.version.ServiceVersionRepository
import com.infosung.atomic.app.version.domain.SemanticVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest

class JpaLoadVersionPolicyAdapterTest {
  @Test
  fun `loadLatestRegistered should map repository entity into domain policy`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val adapter = JpaLoadVersionPolicyAdapter(repository)
    `when`(
            repository
                .findFirstByServiceAndPlatformOrderByMainVersionDescMinorVersionDescPatchNumberDesc(
                    "MY_SERVICE",
                    "ANDROID",
                ),
        )
        .thenReturn(
            entity(
                main = 2,
                minor = 0,
                patch = 0,
                requireUpdate = false,
                storeAvailable = true,
            ),
        )

    val result = adapter.loadLatestRegistered(service = "MY_SERVICE", platform = "ANDROID")

    requireNotNull(result)
    assertEquals("MY_SERVICE", result.service)
    assertEquals("ANDROID", result.platform)
    assertEquals(SemanticVersion(major = 2, minor = 0, patch = 0), result.version)
    assertEquals(true, result.storeAvailable)
    assertEquals(false, result.requireUpdate)
  }

  @Test
  fun `loadRequiredUpdateTargetAbove should request only one row and map first result`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val adapter = JpaLoadVersionPolicyAdapter(repository)
    val clientVersion = SemanticVersion(major = 1, minor = 2, patch = 3)
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
                entity(
                    main = 1,
                    minor = 2,
                    patch = 4,
                    requireUpdate = true,
                    storeAvailable = true,
                    storeUrl = "https://force.update",
                ),
            ),
        )

    val result =
        adapter.loadRequiredUpdateTargetAbove(
            service = "MY_SERVICE",
            platform = "ANDROID",
            version = clientVersion,
        )

    requireNotNull(result)
    assertEquals(SemanticVersion(major = 1, minor = 2, patch = 4), result.version)
    assertEquals("https://force.update", result.storeUrl)
    verify(repository)
        .findRequiredUpdateTargetsHigherThan(
            "MY_SERVICE",
            "ANDROID",
            1,
            2,
            3,
            PageRequest.of(0, 1),
        )
  }

  @Test
  fun `loadExact should return null when repository row is absent`() {
    val repository = mock(ServiceVersionRepository::class.java)
    val adapter = JpaLoadVersionPolicyAdapter(repository)
    val version = SemanticVersion(major = 1, minor = 2, patch = 3)
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

    val result =
        adapter.loadExact(
            service = "MY_SERVICE",
            platform = "ANDROID",
            version = version,
        )

    assertNull(result)
  }

  private fun entity(
      main: Int,
      minor: Int,
      patch: Int,
      requireUpdate: Boolean,
      storeAvailable: Boolean,
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
        .also { it.storeAvailable = storeAvailable }
  }
}
