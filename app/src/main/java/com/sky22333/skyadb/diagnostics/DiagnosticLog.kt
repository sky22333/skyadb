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
    App("应用"),
    WifiAdb("Wi-Fi ADB"),
    UsbOtg("USB OTG"),
    Fastboot("Fastboot"),
    Pairing("无线配对"),
    Discovery("设备发现"),
    Files("文件"),
    Apps("应用管理"),
    Install("安装"),
    Shell("Shell"),
    Screenshot("截图"),
    Logs("系统日志"),
    Remote("遥控器"),
    Mirror("屏幕镜像"),
    Download("下载"),
    Settings("设置"),
}
