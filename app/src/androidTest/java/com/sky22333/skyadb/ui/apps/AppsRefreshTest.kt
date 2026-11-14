package com.sky22333.skyadb.ui.apps

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sky22333.skyadb.MainActivity
import com.sky22333.skyadb.model.AppInfo
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsRefreshTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun refreshReloadsVisibleIcon() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n com.sky22333.skyadb/.MainActivity")).use { it.readBytes() }
        var activity: MainActivity? = null
        withTimeout(10_000) {
            while (activity == null) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
                if (activity == null) delay(50)
            }
        }
        val calls = AtomicInteger()
        val loader: suspend (String) -> Bitmap? = { calls.incrementAndGet(); Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
        val state = mutableStateOf(AppsUiState(apps = listOf(AppInfo("org.example.app", "Before", false))))
        instrumentation.runOnMainSync {
            requireNotNull(activity).setContent {
                AdbManagerTheme {
                    AppsContent(uiState = state.value, onBackClick = {}, onQueryChanged = {}, onFilterChanged = {},
                        onRefreshClick = {}, loadIcon = loader, onLaunchClick = {}, onStopClick = {},
                        onSetEnabledClick = { _, _ -> }, onExportClick = {}, onUninstallClick = {},
                        onCancelPendingAction = {}, onConfirmPendingAction = {})
                }
            }
        }
        compose.waitUntil(10_000) { calls.get() == 1 }
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        compose.runOnIdle { state.value = state.value.copy(loading = false, apps = listOf(AppInfo("org.example.app", "After", false)), iconRevision = state.value.iconRevision + 1) }
        compose.waitUntil(10_000) { calls.get() == 2 }
        assertEquals("A refreshed package must reload its icon", 2, calls.get())
    }
}
