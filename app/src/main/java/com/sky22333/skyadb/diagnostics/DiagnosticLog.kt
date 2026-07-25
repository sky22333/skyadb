package com.sky22333.skyadb.diagnostics

import androidx.annotation.StringRes
import com.sky22333.skyadb.R

data class DiagnosticLog(
    val id: Long,
    val timeMillis: Long,
    val module: DiagnosticModule,
    val operation: String,
    val target: String? = null,
    val message: String,
    val suggestion: String,
    val errorClass: String? = null,
    val errorMessage: String? = null,
)

enum class DiagnosticModule(@param:StringRes val labelRes: Int) {
    App(R.string.diagnostic_module_app),
    WifiAdb(R.string.diagnostic_module_wifi_adb),
    UsbOtg(R.string.diagnostic_module_usb_otg),
    Fastboot(R.string.diagnostic_module_fastboot),
    Pairing(R.string.diagnostic_module_pairing),
    Discovery(R.string.diagnostic_module_discovery),
    Files(R.string.diagnostic_module_files),
    Apps(R.string.diagnostic_module_apps),
    Install(R.string.diagnostic_module_install),
    Shell(R.string.diagnostic_module_shell),
    Screenshot(R.string.diagnostic_module_screenshot),
    Logs(R.string.diagnostic_module_logs),
    Remote(R.string.diagnostic_module_remote),
    Mirror(R.string.diagnostic_module_mirror),
    Download(R.string.diagnostic_module_download),
    Settings(R.string.diagnostic_module_settings),
}
