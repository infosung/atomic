package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.`in`.web.AppOauthRedirectController
import com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.EntityOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore
import com.infosung.atomic.app.oauth.adapter.out.relay.store.OauthRelayCodeStore
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectApplicationException
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectErrorCode
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRemoteFailureException
import com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException
import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeApplicationException
import com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeRequestException
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAppleCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildAuthorizationRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.BuildOauthCallbackRedirectUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.ConsumeOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.application.port.`in`.IssueOauthRelayCodeUseCase
import com.infosung.atomic.app.oauth.autoconfigure.AtomicAppOauthRedirectProperties
import com.infosung.atomic.app.oauth.domain.OauthRelayPayload
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.oauth.api.OauthProviderName
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper

class OauthRedirectPublicContractTest {
  @Test
  fun `oauth relay payload constructor contract should remain stable`() {
    assertEquals(
        listOf(
            "provider",
            "accessToken",
            "refreshToken",
            "idToken",
            "tokenType",
            "expiresInSeconds",
            "scopes",
            "raw",
            "nonce",
            "stateAttributes",
        ),
        OauthRelayPayload::class.primaryConstructor!!.parameters.mapNotNull { it.name },
    )
  }

  @Test
  fun `oauth redirect properties public structure should remain stable`() {
    assertEquals(
        listOf(
                "allowedRedirectUriPrefixes",
                "callbackBinding",
                "callbackEndpointPath",
                "enabled",
                "redirectEndpointPath",
                "relayCodeQueryParameterName",
                "relayCodeTtlSeconds",
                "store",
            )
            .sorted(),
        AtomicAppOauthRedirectProperties::class.memberProperties.map { it.name }.sorted(),
    )
    assertEquals(
        listOf(
                "enabled",
                "mode",
                "stateAttributeKey",
                "cookieName",
                "cookieSameSite",
                "cookiePath",
                "cookieSecure",
                "cookieMaxAgeSeconds",
            )
            .sorted(),
        AtomicAppOauthRedirectProperties.CallbackBinding::class
            .memberProperties
            .map { it.name }
            .sorted(),
    )
    assertEquals(
        listOf("type", "failFast", "inMemory", "cache", "entity").sorted(),
        AtomicAppOauthRedirectProperties.Store::class.memberProperties.map { it.name }.sorted(),
    )
    assertEquals(
        listOf("cleanupInterval"),
        AtomicAppOauthRedirectProperties.InMemory::class.memberProperties.map { it.name },
    )
    assertEquals(
        listOf("cacheName", "keyPrefix", "ttlSeconds").sorted(),
        AtomicAppOauthRedirectProperties.Cache::class.memberProperties.map { it.name }.sorted(),
    )
    assertEquals(
        listOf("tableName"),
        AtomicAppOauthRedirectProperties.Entity::class.memberProperties.map { it.name },
    )
  }

  @Test
  fun `oauth redirect use-case public methods should remain stable`() {
    assertEquals(
        listOf(
            "build(String, String, String, String, String, String, Map, String):AuthorizationRedirectResult",
        ),
        publicSignatures(BuildAuthorizationRedirectUseCase::class.java),
    )
    assertEquals(
        listOf(
            "build(String, String, String, Map, String):CallbackRedirectResult",
        ),
        publicSignatures(BuildOauthCallbackRedirectUseCase::class.java),
    )
    assertEquals(
        listOf(
            "build(String, String, String, String, Map, String):CallbackRedirectResult",
        ),
        publicSignatures(BuildAppleCallbackRedirectUseCase::class.java),
    )
  }

  @Test
  fun `oauth redirect web entry type should remain exported from adapter in web`() {
    assertTrue(Modifier.isPublic(AppOauthRedirectController::class.java.modifiers))
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.in.web.AppOauthRedirectController",
        AppOauthRedirectController::class.java.name,
    )
  }

  @Test
  fun `oauth redirect public error seams should remain exported from application exception`() {
    assertEquals(
        "com.infosung.atomic.app.oauth.application.exception.OauthRedirectApplicationException",
        OauthRedirectApplicationException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.application.exception.OauthRedirectRequestException",
        OauthRedirectRequestException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.application.exception.OauthRedirectRemoteFailureException",
        OauthRedirectRemoteFailureException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeApplicationException",
        OauthRelayCodeApplicationException::class.java.name,
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.application.exception.OauthRelayCodeRequestException",
        OauthRelayCodeRequestException::class.java.name,
    )
    assertEquals(
        listOf(
            Triple("OAUTH_REDIRECT_INVALID_REQUEST", 400, "OAuth redirect request is invalid."),
            Triple("OAUTH_CALLBACK_INVALID_REQUEST", 400, "OAuth callback request is invalid."),
            Triple("OAUTH_PROVIDER_UNSUPPORTED", 400, "OAuth provider is not supported."),
            Triple("OAUTH_REDIRECT_URI_INVALID", 400, "OAuth redirect URI is invalid."),
            Triple("OAUTH_CALLBACK_BINDING_INVALID", 400, "OAuth callback binding is invalid."),
            Triple("OAUTH_STATE_INVALID", 400, "OAuth callback state is invalid."),
            Triple("OAUTH_PROVIDER_REMOTE_FAILURE", 500, "Upstream OAuth provider request failed."),
            Triple(
                "OAUTH_APPLE_CALLBACK_POST_ONLY",
                400,
                "Apple callback supports POST form_post only.",
            ),
            Triple(
                "OAUTH_REDIRECT_CONFIGURATION_INVALID",
                500,
                "OAuth redirect configuration is invalid.",
            ),
            Triple("OAUTH_RELAY_CODE_REQUIRED", 400, "OAuth relay code is required."),
            Triple("OAUTH_RELAY_CODE_INVALID_REQUEST", 400, "OAuth relay code request is invalid."),
        ),
        OauthRedirectErrorCode.entries.map {
          Triple(it.name, it.defaultHttpStatus, it.defaultMessage)
        },
    )
    assertTrue(
        OauthRedirectApplicationException::class.memberProperties.any { it.name == "errorCode" },
    )
  }

  @Test
  fun `oauth relay use-case public methods should remain stable`() {
    assertEquals(
        listOf(
            "consume(String):OauthRelayPayload",
        ),
        publicSignatures(ConsumeOauthRelayCodeUseCase::class.java),
    )
    assertEquals(
        listOf(
            "issue(OauthRelayPayload):String",
        ),
        publicSignatures(IssueOauthRelayCodeUseCase::class.java),
    )
  }

  @Test
  fun `oauth relay store seam should remain exported from adapter out relay store`() {
    assertEquals(
        listOf(
            "pop(String, Instant):OauthRelayPayload",
            "save(String, OauthRelayPayload, Instant):void",
        ),
        publicSignatures(OauthRelayCodeStore::class.java),
    )
  }

  @Test
  fun `in memory relay store public constructor contract should remain stable`() {
    assertEquals(
        listOf("cleanupInterval", "timeProvider"),
        InMemoryOauthRelayCodeStore::class.primaryConstructor!!.parameters.mapNotNull { it.name },
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.InMemoryOauthRelayCodeStore",
        InMemoryOauthRelayCodeStore::class.java.name,
    )
  }

  @Test
  fun `cache relay store public constructor contract should remain stable`() {
    assertEquals(
        listOf(
            "cacheManager",
            "cacheName",
            "keyPrefix",
            "ttlSeconds",
            "objectMapper",
            "timeProvider",
        ),
        CacheOauthRelayCodeStore::class.primaryConstructor!!.parameters.mapNotNull { it.name },
    )
    assertEquals(
        "com.infosung.atomic.app.oauth.adapter.out.relay.store.CacheOauthRelayCodeStore",
        CacheOauthRelayCodeStore::class.java.name,
    )
  }

  @Test
  fun `entity relay code store should reject unsafe table names`() {
    val exception =
        assertFailsWith<IllegalArgumentException> {
          EntityOauthRelayCodeStore(
              jdbcOperations = capturingJdbcOperations().jdbcOperations,
              transactionTemplate = TransactionTemplate(NoOpTransactionManager()),
              objectMapper = jacksonObjectMapper(),
              tableName = "atomic-oauth-relay-code",
          )
        }

    assertEquals(
        "atomic.app.oauth.redirect.store.entity.table-name must contain only letters, numbers, and underscores.",
        exception.message,
    )
  }

  @Test
  fun `entity relay code store save should use stable table and columns`() {
    val now = Instant.parse("2026-03-14T00:00:00Z")
    val capturedJdbc = capturingJdbcOperations()
    val store =
        EntityOauthRelayCodeStore(
            jdbcOperations = capturedJdbc.jdbcOperations,
            transactionTemplate = TransactionTemplate(NoOpTransactionManager()),
            objectMapper = jacksonObjectMapper(),
            timeProvider = TimeProvider(Clock.fixed(now, ZoneOffset.UTC)),
            tableName = "atomic_oauth_relay_code",
        )

    store.save(
        relayCode = "relay-1",
        payload =
            OauthRelayPayload(
                provider = OauthProviderName.GOOGLE,
                accessToken = "access-token",
                nonce = "nonce-1",
            ),
        expiresAt = now.plusSeconds(300),
    )

    assertEquals(
        "DELETE FROM atomic_oauth_relay_code WHERE relay_code = ?",
        normalizeSql(capturedJdbc.updates[0].sql),
    )
    assertEquals(listOf("relay-1"), capturedJdbc.updates[0].arguments)
    assertEquals(
        "INSERT INTO atomic_oauth_relay_code (relay_code, payload_json, expires_at, created_at) VALUES (?, ?, ?, ?)",
        normalizeSql(capturedJdbc.updates[1].sql),
    )
    assertEquals("relay-1", capturedJdbc.updates[1].arguments[0])
    assertTrue((capturedJdbc.updates[1].arguments[1] as String).contains("\"provider\":\"GOOGLE\""))
    assertEquals(Timestamp.from(now.plusSeconds(300)), capturedJdbc.updates[1].arguments[2])
    assertEquals(Timestamp.from(now), capturedJdbc.updates[1].arguments[3])
  }

  @Test
  fun `entity relay code store pop should use stable select columns and delete by relay code`() {
    val now = Instant.parse("2026-03-14T00:00:00Z")
    val objectMapper = jacksonObjectMapper()
    val payload =
        OauthRelayPayload(
            provider = OauthProviderName.APPLE,
            idToken = "id-token",
            nonce = "nonce-2",
        )
    val capturedJdbc =
        capturingJdbcOperations(
            selectedPayloadJson = objectMapper.writeValueAsString(payload),
            selectedExpiresAt = now.plusSeconds(300),
        )
    val store =
        EntityOauthRelayCodeStore(
            jdbcOperations = capturedJdbc.jdbcOperations,
            transactionTemplate = TransactionTemplate(NoOpTransactionManager()),
            objectMapper = objectMapper,
            tableName = "atomic_oauth_relay_code",
        )

    val popped = store.pop("relay-2", now)

    assertNotNull(popped)
    assertEquals(OauthProviderName.APPLE, popped.provider)
    assertEquals("id-token", popped.idToken)
    assertEquals(
        "SELECT payload_json, expires_at FROM atomic_oauth_relay_code WHERE relay_code = ? FOR UPDATE",
        normalizeSql(capturedJdbc.querySql),
    )
    assertEquals(listOf("relay-2"), capturedJdbc.queryArguments)
    assertEquals(
        "DELETE FROM atomic_oauth_relay_code WHERE relay_code = ?",
        normalizeSql(capturedJdbc.updates.last().sql),
    )
    assertEquals(listOf("relay-2"), capturedJdbc.updates.last().arguments)
  }

  private fun publicSignatures(type: Class<*>): List<String> {
    return type.declaredMethods
        .filter {
          Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.name.contains("\$default")
        }
        .map { method ->
          val parameters = method.parameterTypes.joinToString(", ") { it.simpleName }
          "${method.name}($parameters):${method.returnType.simpleName}"
        }
        .sorted()
  }

  private fun normalizeSql(sql: String): String = sql.replace(Regex("\\s+"), " ").trim()

  private fun capturingJdbcOperations(
      selectedPayloadJson: String? = null,
      selectedExpiresAt: Instant? = null,
  ): CapturingJdbcOperations {
    return CapturingJdbcOperations(selectedPayloadJson, selectedExpiresAt)
  }

  private class NoOpTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
        SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
  }

  private class CapturingJdbcOperations(
      private val selectedPayloadJson: String?,
      private val selectedExpiresAt: Instant?,
  ) {
    val updates = mutableListOf<SqlInvocation>()
    var querySql: String = ""
    var queryArguments: List<Any?> = emptyList()

    val jdbcOperations: JdbcOperations =
        Proxy.newProxyInstance(
            JdbcOperations::class.java.classLoader,
            arrayOf(JdbcOperations::class.java),
        ) { _, method, args ->
          when (method.name) {
            "update" -> {
              updates +=
                  SqlInvocation(
                      sql = args[0] as String,
                      arguments = flattenArguments(args.drop(1)),
                  )
              1
            }

            "query" -> {
              querySql = args[0] as String
              queryArguments = flattenArguments(args.drop(2))
              if (selectedPayloadJson == null || selectedExpiresAt == null) {
                null
              } else {
                @Suppress("UNCHECKED_CAST") val extractor = args[1] as ResultSetExtractor<Any?>
                extractor.extractData(fakeResultSet(selectedPayloadJson, selectedExpiresAt))
              }
            }

            "toString" -> "CapturingJdbcOperations"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> false
            else -> unsupported(method.returnType)
          }
        } as JdbcOperations

    private fun fakeResultSet(
        payloadJson: String,
        expiresAt: Instant,
    ): ResultSet {
      var nextCalls = 0
      return Proxy.newProxyInstance(
          ResultSet::class.java.classLoader,
          arrayOf(ResultSet::class.java),
      ) { _, method, args ->
        when (method.name) {
          "next" -> nextCalls++ == 0
          "getString" -> payloadJson
          "getTimestamp" -> Timestamp.from(expiresAt)
          "close" -> Unit
          "toString" -> "FakeResultSet"
          "hashCode" -> System.identityHashCode(this)
          "equals" -> false
          else -> unsupported(method.returnType)
        }
      } as ResultSet
    }

    private fun unsupported(returnType: Class<*>): Any? {
      return when {
        returnType == Boolean::class.javaPrimitiveType -> false
        returnType == Int::class.javaPrimitiveType -> 0
        returnType == Long::class.javaPrimitiveType -> 0L
        returnType == Double::class.javaPrimitiveType -> 0.0
        returnType == Float::class.javaPrimitiveType -> 0f
        returnType == Short::class.javaPrimitiveType -> 0.toShort()
        returnType == Byte::class.javaPrimitiveType -> 0.toByte()
        returnType == Char::class.javaPrimitiveType -> '\u0000'
        else -> null
      }
    }

    private fun flattenArguments(arguments: List<Any?>): List<Any?> {
      return arguments.flatMap { argument ->
        when (argument) {
          is Array<*> -> argument.toList()
          else -> listOf(argument)
        }
      }
    }
  }

  private data class SqlInvocation(
      val sql: String,
      val arguments: List<Any?>,
  )
}
