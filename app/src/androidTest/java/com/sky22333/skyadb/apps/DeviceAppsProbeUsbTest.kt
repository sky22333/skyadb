package com.sky22333.skyadb.apps

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flyfishxu.kadb.usb.KadbUsb
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAppsProbeUsbTest {
    @Test fun readsLabelsAndIconsFromUsbTarget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val usb = context.getSystemService(UsbManager::class.java)
        val device = KadbUsb.devices(usb).firstOrNull { usb.hasPermission(it) }
        assumeTrue("An authorized USB ADB target is required", device != null)
        val manager = KadbManager()
        try {
            val result = manager.connectUsb(usb, requireNotNull(device))
            check(result is AdbOperationResult.Success) { "USB connection failed: $result" }
            val probe = DeviceAppsProbe.open(context, manager)
            try {
                val apps = probe.list()
                assertTrue("Expected at least one resolved app label", apps.any { it.label != it.packageName })
                assertNotNull("Expected a launcher icon", probe.icon("com.android.settings"))
            } finally { probe.close() }
        } finally { manager.disconnect() }
    }
}
