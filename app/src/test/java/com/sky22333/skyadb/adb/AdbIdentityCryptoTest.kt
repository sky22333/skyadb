package com.sky22333.skyadb.adb

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kadb 身份流程要求 PKCS#8 解析结果为 RSAPrivateCrtKey。 */
class AdbIdentityCryptoTest {
    @Test
    fun pkcs8RoundTrip_yieldsCrtKey_soIdentityAndPairingSucceed() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        }.generateKeyPair()

        val parsed = parsePrivateKeyFromPem(encodePrivateKeyPem(keyPair.private.encoded))
        assertTrue("PKCS#8 解析结果必须是 RSAPrivateCrtKey", parsed is RSAPrivateCrtKey)

        val crt = parsed as RSAPrivateCrtKey
        val derivedPublic = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(crt.modulus, crt.publicExponent))
        assertEquals(keyPair.public.encoded.toList(), derivedPublic.encoded.toList())
    }

    @Test
    fun plainRsaPrivateKey_isNotCrt_reproducesOldAndroidFailureMode() {
        val crt = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            .generateKeyPair().private as RSAPrivateCrtKey
        val plain = KeyFactory.getInstance("RSA")
            .generatePrivate(RSAPrivateKeySpec(crt.modulus, crt.privateExponent))

        assertFalse("非 CRT 私钥", plain is RSAPrivateCrtKey)
    }

    private fun encodePrivateKeyPem(encoded: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(encoded)
        return "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----"
    }

    private fun parsePrivateKeyFromPem(pem: String) =
        KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\r", "")
                        .replace("\n", "")
                        .trim(),
                ),
            ),
        )
}
