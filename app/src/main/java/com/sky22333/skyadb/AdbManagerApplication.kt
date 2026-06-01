package com.sky22333.skyadb

import android.app.Application
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber

class AdbManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        installBouncyCastleProvider()
        AppServices.initialize(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    // 旧系统 PKCS#8 解析 RSA 私钥缺 CRT，Kadb 身份生成会失败；置顶 BC 修复，TLS 仍走平台 provider。
    private fun installBouncyCastleProvider() {
        val position = runCatching {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }.getOrElse { error ->
            Log.w(TAG, "BouncyCastle provider 注册失败", error)
            -1
        }
        logRsaKeyFactoryHealth(position)
    }

    private fun logRsaKeyFactoryHealth(insertedAt: Int) {
        runCatching {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(512, SecureRandom.getInstance("SHA1PRNG"))
            }.generateKeyPair()
            val factory = KeyFactory.getInstance("RSA")
            val parsed = factory.generatePrivate(PKCS8EncodedKeySpec(keyPair.private.encoded))
            Log.i(TAG, "BC insertedAt=$insertedAt, RSA provider=${factory.provider.name}, isCrt=${parsed is RSAPrivateCrtKey}")
        }.onFailure { Log.w(TAG, "RSA 自检失败", it) }
    }

    private companion object {
        const val TAG = "SkyadbCrypto"
    }
}
