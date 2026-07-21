package com.sky22333.skyadb.adb

import android.content.Context
import android.util.Log
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.sky22333.skyadb.diagnostics.DiagnosticLogger
import com.sky22333.skyadb.diagnostics.DiagnosticModule
import java.io.File
import okio.Path.Companion.toPath

/** Kadb 主机身份持久化配置。 */
object AdbIdentityManager {
    private const val Tag = "SkyadbIdentity"

    fun initialize(context: Context) {
        val keyFile = File(context.filesDir, "adb_identity/adbkey.pem")
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
            policy = KadbCertPolicy(autoHealInvalidPrivateKey = true),
        )
        runCatching {
            KadbCert.ensureReady()
        }.onFailure { error ->
            Log.w(Tag, "ADB identity initialization failed", error)
            DiagnosticLogger.record(
                module = DiagnosticModule.App,
                operation = "初始化 ADB 身份",
                message = "ADB 身份初始化失败",
                suggestion = "请重启应用后重试连接；如果目标设备弹出授权窗口，请重新允许调试授权。",
                cause = error,
            )
        }
    }
}
