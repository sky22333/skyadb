package com.sky22333.skyadb

import android.app.Application
import java.security.Security
import org.conscrypt.Conscrypt
import timber.log.Timber

class AdbManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        installConscryptProvider()
        AppServices.initialize(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    // Android 10 及以下的平台 Conscrypt 从 PKCS#8 解析 RSA 私钥时返回非 CRT 实例，
    // 导致 Kadb 生成 ADB 身份/证书时抛 "Only RSA private keys with CRT parameters are supported"，
    // 连接与无线配对均失败。置顶新版 Conscrypt 后可返回 RSAPrivateCrtKey，并为旧设备配对提供 TLS 1.3。
    private fun installConscryptProvider() {
        runCatching {
            if (Conscrypt.isAvailable()) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            }
        }.onFailure { Timber.w(it, "Conscrypt provider 注册失败，回退到平台默认 provider") }
    }
}
