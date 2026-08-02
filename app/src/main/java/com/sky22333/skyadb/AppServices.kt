package com.sky22333.skyadb

import android.content.Context
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.adb.FastbootOtgManager
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.data.RecentDeviceStore
import com.sky22333.skyadb.discovery.AndroidAdbMdnsDiscovery
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.discovery.LanAdbScanner
import com.sky22333.skyadb.discovery.NetworkInfoProvider
import com.sky22333.skyadb.download.NetworkDownloadManager
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.localapps.LocalAppExporter
import com.sky22333.skyadb.repository.DefaultAdbRepository
import com.sky22333.skyadb.scrcpy.ScrcpyRepository
import com.sky22333.skyadb.usb.UsbOtgHost
import com.sky22333.skyadb.usb.UsbOtgActions

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

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
