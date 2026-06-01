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

/**
 * 复刻 Kadb CertUtils 的「生成 → PKCS#8 PEM → 重新解析 → 派生公钥」身份流程。
 * 连接与无线配对（Android 6~8）共用这条 private-key-first 路径，其前提是：
 * 重新解析出的私钥必须是 RSAPrivateCrtKey，否则派生公钥失败并抛
 * "Only RSA private keys with CRT parameters are supported"。
 * 旧版平台 Conscrypt 会返回非 CRT 私钥触发该错误，置顶新版 Conscrypt 后修复。
 */
class AdbIdentityCryptoTest {
    @Test
    fun pkcs8RoundTrip_yieldsCrtKey_soIdentityAndPairingSucceed() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
        }.generateKeyPair()

        val parsed = parsePrivateKeyFromPem(encodePrivateKeyPem(keyPair.private.encoded))
        assertTrue(
            "PKCS#8 解析必须返回 RSAPrivateCrtKey，否则连接/配对会失败",
            parsed is RSAPrivateCrtKey,
        )

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

        assertFalse("非 CRT 私钥即旧版 Conscrypt 的返回类型", plain is RSAPrivateCrtKey)
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
