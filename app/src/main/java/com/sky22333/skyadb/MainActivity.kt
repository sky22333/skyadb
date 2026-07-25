package com.sky22333.skyadb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.sky22333.skyadb.data.ThemeMode
import com.sky22333.skyadb.ui.AdbManagerApp
import com.sky22333.skyadb.ui.theme.AdbManagerTheme

class MainActivity : AppCompatActivity() {
    private val usbPermissionAction = "${BuildConfig.APPLICATION_ID}.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                usbPermissionAction -> {
                    val device = intent.deviceExtra()
                    if (device == null) return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        AppServices.usbOtgActions.onPermissionGranted(device.deviceName)
                    } else {
                        AppServices.usbOtgActions.onPermissionDenied(device.deviceName)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    AppServices.usbOtgActions.refresh()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.deviceExtra()
                    if (device != null) {
                        AppServices.usbOtgActions.onDeviceDetached(device.deviceName)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        bindUsbOtgActions()
        registerUsbReceiver()
        setContent {
            val settings by AppServices.settingsStore.settings.collectAsState(
                initial = com.sky22333.skyadb.data.AppSettings(),
            )
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> systemInDarkTheme
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            LocalNetworkPermissionRequester()

            AdbManagerTheme(darkTheme = darkTheme) {
                AdbManagerApp()
            }
        }
        handleUsbIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        AppServices.usbOtgActions.requestPermission = {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun bindUsbOtgActions() {
        AppServices.usbOtgActions.requestPermission = ::requestUsbPermission
        AppServices.usbOtgActions.refresh()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            AppServices.usbOtgActions.refresh()
        }
    }

    private fun requestUsbPermission(deviceName: String) {
        val device = AppServices.usbOtgHost.getDevice(deviceName) ?: return
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val intent = Intent(usbPermissionAction).setPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            device.deviceId,
            intent,
            flags,
        )
        AppServices.usbOtgHost.requestPermission(device, pendingIntent)
    }

    private fun Intent.deviceExtra(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }
}

@Composable
private fun LocalNetworkPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val context = LocalContext.current
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(permission)
        }
    }
}
