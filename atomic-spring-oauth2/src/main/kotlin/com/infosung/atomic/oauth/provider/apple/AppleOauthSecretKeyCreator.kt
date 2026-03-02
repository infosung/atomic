package com.infosung.atomic.oauth.provider.apple

import com.infosung.atomic.contract.time.DateUtil
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.io.StringReader
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.util.*
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.springframework.core.io.ClassPathResource

/**
 * Creates and caches Apple client-secret JWT for token endpoints.
 */
class AppleOauthSecretKeyCreator(
    private val kid: String,
    private val bundleId: String,
    private val iss: String,
    private val alg: String = "ES256",
    private val aud: String = "https://appleid.apple.com",
    resourcePath: String,
) {
  private val privateKey: PrivateKey = getPrivateKey(resourcePath)

  @Volatile private var secretKeyExpiredAt: Date = Date()

  @Volatile private var secretKey: String = ""

  /**
   * Returns cached client secret token, recreating when expired.
   */
  fun getOauthSecretKey(): String {
    val now = Date()
    if (secretKey.isEmpty() || secretKeyExpiredAt.before(now)) {
      synchronized(this) {
        if (secretKey.isEmpty() || secretKeyExpiredAt.before(now)) {
          val newKey = createAppleOauthSecretKey()
          secretKey = newKey.first
          secretKeyExpiredAt = newKey.second
        }
      }
    }
    return secretKey
  }

  private fun createAppleOauthSecretKey(): Pair<String, Date> {
    val iat = Date()
    val exp = DateUtil.plusTime(iat, Calendar.MINUTE, 59)
    val jwsAlgorithm = JWSAlgorithm.parse(alg)

    val claims =
        JWTClaimsSet.Builder()
            .issuer(iss)
            .issueTime(iat)
            .expirationTime(exp)
            .audience(aud)
            .subject(bundleId)
            .build()
    val header = JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType.JWT).keyID(kid).build()
    val signer =
        ECDSASigner(
            privateKey as? ECPrivateKey
                ?: throw IllegalStateException("Apple private key must be an EC private key."),
        )

    val token =
        try {
          SignedJWT(header, claims).apply { sign(signer) }.serialize()
        } catch (e: JOSEException) {
          throw IllegalStateException("Failed to create Apple OAuth secret key.", e)
        }
    return token to exp
  }

  private fun getPrivateKey(resourcePath: String): PrivateKey {
    val privateKey = getPrivateKeyFile(resourcePath)
    val pemReader = StringReader(privateKey)
    val pemParser = PEMParser(pemReader)
    val converter = JcaPEMKeyConverter()
    val privateKeyInfo = pemParser.readObject() as PrivateKeyInfo
    return converter.getPrivateKey(privateKeyInfo)
  }

  /**
   * Reads private key PEM from classpath.
   */
  fun getPrivateKeyFile(resourcePath: String): String {
    val resource = ClassPathResource(resourcePath)
    return resource.inputStream.bufferedReader().use { it.readText() }
  }
}
