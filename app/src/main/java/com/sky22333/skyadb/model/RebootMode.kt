package com.sky22333.skyadb.model

import androidx.annotation.StringRes
import com.sky22333.skyadb.R

enum class RebootMode(@param:StringRes val label: Int, val adbTarget: String, val fastbootCommand: String) {
    System(R.string.fastboot_reboot_system, "", "reboot"),
    Bootloader(R.string.fastboot_reboot_bootloader, "bootloader", "reboot-bootloader"),
    Recovery(R.string.fastboot_reboot_recovery, "recovery", "reboot-recovery"),
    Fastbootd(R.string.fastboot_reboot_fastbootd, "fastboot", "reboot-fastboot"),
}
