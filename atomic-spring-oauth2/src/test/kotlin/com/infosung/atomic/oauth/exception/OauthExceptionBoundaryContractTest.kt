package com.infosung.atomic.oauth.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OauthExceptionBoundaryContractTest {
  @Test
  fun `foundation oauth exceptions should stay in oauth hierarchy without http status inheritance`() {
    assertTrue(OauthException::class.java.isAssignableFrom(HttpJwtVerifyException::class.java))
    assertTrue(OauthException::class.java.isAssignableFrom(HttpIOException::class.java))
    assertFalse(
        HttpStatusException::class.java.isAssignableFrom(HttpJwtVerifyException::class.java),
    )
    assertFalse(
        HttpStatusException::class.java.isAssignableFrom(HttpIOException::class.java),
    )
  }
}
