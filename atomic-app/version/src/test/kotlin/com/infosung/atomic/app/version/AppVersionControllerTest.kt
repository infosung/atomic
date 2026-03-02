package com.infosung.atomic.app.version

import com.infosung.atomic.contract.exception.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AppVersionControllerTest {
  @Test
  fun `getVersion should return 400 when service header is missing`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)

    val error =
        assertFailsWith<HttpStatusException> {
          controller.getVersion(
              serviceNameHeader = null,
              platformHeader = "ANDROID",
              appVersionHeader = "1.2.3",
          )
        }

    assertEquals(400, error.status)
  }

  @Test
  fun `getVersion should return 400 when platform header is missing`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)

    val error =
        assertFailsWith<HttpStatusException> {
          controller.getVersion(
              serviceNameHeader = "MY_SERVICE",
              platformHeader = null,
              appVersionHeader = "1.2.3",
          )
        }

    assertEquals(400, error.status)
  }

  @Test
  fun `getVersion should return 400 when appVersion header is missing`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)

    val error =
        assertFailsWith<HttpStatusException> {
          controller.getVersion(
              serviceNameHeader = "MY_SERVICE",
              platformHeader = "ANDROID",
              appVersionHeader = null,
          )
        }

    assertEquals(400, error.status)
  }

  @Test
  fun `getVersion should call service and return response when headers are valid`() {
    val service = mock(AppVersionCheckService::class.java)
    val controller = AppVersionController(service)
    val expected =
        VersionCheckResult(
            currentVersion = "1.2.4",
            userVersion = "1.2.3",
            requiredUpdate = true,
            storeUrl = "https://force.update",
        )
    val request =
        VersionCheckRequest(
            service = "MY_SERVICE",
            platform = "ANDROID",
            appVersion = "1.2.3",
        )
    `when`(service.checkVersion(request)).thenReturn(expected)

    val response =
        controller.getVersion(
            serviceNameHeader = "MY_SERVICE",
            platformHeader = "ANDROID",
            appVersionHeader = "1.2.3",
        )

    assertEquals("OK", response.code)
    val payload = assertNotNull(response.data)
    assertEquals(expected, payload)

    verify(service).checkVersion(request)
  }
}
