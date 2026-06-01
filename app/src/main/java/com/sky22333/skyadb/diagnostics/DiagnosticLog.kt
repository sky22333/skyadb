package com.sky22333.skyadb.diagnostics

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

enum class DiagnosticModule(val label: String) {
    WifiAdb("Wi-Fi ADB"),
    UsbAdb("USB ADB"),
    Fastboot("Fastboot"),
    Files("文件"),
    Apps("应用"),
    Install("安装"),
    Shell("Shell"),
    Screenshot("截图"),
    Logs("系统日志"),
    Remote("遥控器"),
    Download("下载"),
    Settings("设置"),
}
