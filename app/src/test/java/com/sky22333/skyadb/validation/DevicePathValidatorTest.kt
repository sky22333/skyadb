package com.sky22333.skyadb.validation

import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePathValidatorTest {
    @Test
    fun pathError_acceptsAbsoluteDevicePath() {
        assertNull(DevicePathValidator.pathError("/sdcard/Download/"))
        assertNull(DevicePathValidator.pathError("/sdcard/Download/file.txt"))
    }

    @Test
    fun pathError_rejectsRelativePath() {
        assertEquals(
            AppText.Res(R.string.error_device_path_prefix, AppText.Res(R.string.unit_device_path)),
            DevicePathValidator.pathError("sdcard/Download/file.txt"),
        )
        assertEquals(
            AppText.Res(R.string.error_device_path_prefix, AppText.Res(R.string.download_target_path_label)),
            DevicePathValidator.pathError("sdcard/Download/", labelRes = R.string.download_target_path_label),
        )
    }
}
