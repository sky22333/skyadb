package com.sky22333.skyadb.model

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString

data class DeviceInfo(
    val brand: String = appString(R.string.unknown),
    val manufacturer: String = appString(R.string.unknown),
    val model: String = appString(R.string.unknown),
    val device: String = appString(R.string.unknown),
    val androidVersion: String = appString(R.string.unknown),
    val sdk: String = appString(R.string.unknown),
    val abi: String = appString(R.string.unknown),
    val buildId: String = appString(R.string.unknown),
    val securityPatch: String = appString(R.string.unknown),
    val resolution: String = appString(R.string.unknown),
    val battery: String = appString(R.string.unknown),
)
