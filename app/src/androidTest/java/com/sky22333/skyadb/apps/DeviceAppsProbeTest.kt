package com.sky22333.skyadb.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sky22333.skyadb.AppServices
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.model.AdbOperationResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import com.flyfishxu.kadb.stream.AdbStream
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.apps.AppsViewModel
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceAppsProbeTest {
    @Test fun refreshRecoversAfterProbeStreamCloses() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("targetHost")
        val port = args.getString("targetPort")?.toIntOrNull()
        assumeTrue("Requires an authorized target device", !host.isNullOrBlank() && port != null)
        val manager = AppServices.kadbManager
        assertTrue(manager.connect(requireNotNull(host), requireNotNull(port)) is AdbOperationResult.Success)
        val viewModel = AppsViewModel()
        try {
            val probe = DeviceAppsProbe.open(AppServices.context, manager)
            assertTrue(probe.list().isNotEmpty())
            val field = AppsViewModel::class.java.getDeclaredField("probe").apply { isAccessible = true }
            field.set(viewModel, probe)
            val stream = DeviceAppsProbe::class.java.getDeclaredField("stream").apply { isAccessible = true }.get(probe) as AdbStream
            stream.close()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.loadApps(force = true) }
            val result = withTimeout(30_000) {
                while (viewModel.uiState.value.operationStatus is OperationStatus.Idle ||
                    viewModel.uiState.value.operationStatus is OperationStatus.Running) delay(50)
                viewModel.uiState.value.operationStatus
            }
            assertTrue("Refresh should restart the probe after stream failure: $result", result is OperationStatus.Success)
        } finally {
            val method = AppsViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
            method.invoke(viewModel)
            manager.disconnect()
        }
    }

    @Test fun listsIconsAndCleansUp() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("targetHost")
        val port = args.getString("targetPort")?.toIntOrNull()
        assumeTrue("Requires an authorized target device", !host.isNullOrBlank() && port != null)
        val manager = KadbManager()
        assertTrue(manager.connect(requireNotNull(host), requireNotNull(port)) is AdbOperationResult.Success)
        try {
            repeat(2) {
                val probe = DeviceAppsProbe.open(AppServices.context, manager)
                try {
                    val apps = probe.list()
                    assertTrue("Expected installed apps", apps.isNotEmpty())
                    assertTrue(apps.all { it.packageName.isNotBlank() && it.label.isNotBlank() })
                    val result = manager.shell("pm list packages") as AdbOperationResult.Success
                    val expected = result.data.output.lineSequence().filter { it.startsWith("package:") }
                        .map { it.removePrefix("package:").trim() }.toSet()
                    assertEquals(expected, apps.map { it.packageName }.toSet())
                    val sample = apps.filterNot { it.isSystem }.take(3) + apps.filter { it.isSystem }.take(1)
                    val icons = coroutineScope { sample.map { app -> async { probe.icon(app.packageName) } }.awaitAll() }
                    assertTrue(icons.isNotEmpty())
                    assertTrue("Expected target-side icons", icons.all { it != null && it.width == 96 })
                } finally {
                    probe.close()
                }
                val result = manager.shell("test ! -e /data/local/tmp/skyadb-app-probe.dex && test ! -e /data/local/tmp/skyadb-app-probe.jar")
                assertTrue(result is AdbOperationResult.Success && result.data.exitCode == 0)
            }
            val interrupted = DeviceAppsProbe.open(AppServices.context, manager)
            assertTrue(interrupted.list().isNotEmpty())
            manager.disconnect()
            runCatching { interrupted.close() }
            assertTrue(manager.connect(requireNotNull(host), requireNotNull(port)) is AdbOperationResult.Success)
            val replacement = DeviceAppsProbe.open(AppServices.context, manager)
            try { assertTrue(replacement.list().isNotEmpty()) } finally { replacement.close() }
            val clean = manager.shell("test ! -e /data/local/tmp/skyadb-app-probe.dex && test ! -e /data/local/tmp/skyadb-app-probe.jar")
            assertTrue(clean is AdbOperationResult.Success && clean.data.exitCode == 0)
        } finally {
            manager.disconnect()
        }
    }
}
