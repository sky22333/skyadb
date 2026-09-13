package com.qwadb.app

import android.content.Context
import com.qwadb.app.adb.KadbManager
import com.qwadb.app.adb.FastbootOtgManager
import com.qwadb.app.data.AppSettingsStore
import com.qwadb.app.data.RecentDeviceStore
import com.qwadb.app.discovery.AndroidAdbMdnsDiscovery
import com.qwadb.app.discovery.AdbMdnsDiscovery
import com.qwadb.app.discovery.LanAdbScanner
import com.qwadb.app.discovery.NetworkInfoProvider
import com.qwadb.app.download.NetworkDownloadManager
import com.qwadb.app.files.LocalFileManager
import com.qwadb.app.localapps.LocalAppExporter
import com.qwadb.app.repository.DefaultAdbRepository
import com.qwadb.app.scrcpy.CameraRepository
import com.qwadb.app.scrcpy.ScrcpyRepository
import com.qwadb.app.status.SystemStatusCollector
import com.qwadb.app.usb.UsbOtgHost
import com.qwadb.app.usb.UsbOtgActions

object AppServices {
    private var appContext: Context? = null

    val context: Context
        get() = requireNotNull(appContext) { "AppServices 尚未初始化 Context" }

    val kadbManager: KadbManager by lazy { KadbManager() }
    val fastbootOtgManager: FastbootOtgManager by lazy { FastbootOtgManager() }
    val usbOtgHost: UsbOtgHost by lazy {
        UsbOtgHost(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val usbOtgActions: UsbOtgActions by lazy {
        UsbOtgActions(usbOtgHost)
    }
    val downloadManager: NetworkDownloadManager by lazy { NetworkDownloadManager(appContext) }
    val localFileManager: LocalFileManager by lazy {
        LocalFileManager(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val localAppExporter: LocalAppExporter by lazy {
        LocalAppExporter(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val settingsStore: AppSettingsStore by lazy {
        AppSettingsStore(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val recentDeviceStore: RecentDeviceStore by lazy {
        RecentDeviceStore(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val networkInfoProvider: NetworkInfoProvider by lazy {
        NetworkInfoProvider(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val lanAdbScanner: LanAdbScanner by lazy { LanAdbScanner() }
    val adbMdnsDiscovery: AdbMdnsDiscovery by lazy {
        AndroidAdbMdnsDiscovery(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val adbRepository: DefaultAdbRepository by lazy {
        DefaultAdbRepository(kadbManager, fastbootOtgManager, usbOtgHost, recentDeviceStore, settingsStore)
    }
    val scrcpyRepository: ScrcpyRepository by lazy {
        ScrcpyRepository(requireNotNull(appContext) { "AppServices 尚未初始化 Context" }, kadbManager)
    }
    val cameraRepository: CameraRepository by lazy {
        CameraRepository(requireNotNull(appContext) { "AppServices 尚未初始化 Context" }, kadbManager)
    }
    val systemStatusCollector: SystemStatusCollector by lazy {
        SystemStatusCollector(kadbManager)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
