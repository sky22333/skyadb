package com.sky22333.skyadb.adb

import android.content.Context
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import java.io.File
import okio.Path.Companion.toPath

/** Kadb 主机身份持久化配置。 */
object AdbIdentityManager {
    fun initialize(context: Context) {
        val keyFile = File(context.filesDir, "adb_identity/adbkey.pem")
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
        )
    }
}
