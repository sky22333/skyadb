package com.qwadb.app.validation

import androidx.annotation.StringRes
import com.qwadb.app.R
import com.qwadb.app.i18n.AppText

object DevicePathValidator {
    fun pathError(value: String, @StringRes labelRes: Int = R.string.unit_device_path): AppText.Res? {
        return when {
            value.isBlank() -> null
            !value.startsWith("/") -> AppText.Res(R.string.error_device_path_prefix, AppText.Res(labelRes))
            else -> null
        }
    }
}
