package com.sky22333.skyadb

import android.content.Context
import com.sky22333.skyadb.adb.KadbManager
import com.sky22333.skyadb.data.AppSettingsStore
import com.sky22333.skyadb.data.RecentDeviceStore
import com.sky22333.skyadb.discovery.AndroidAdbMdnsDiscovery
import com.sky22333.skyadb.discovery.AdbMdnsDiscovery
import com.sky22333.skyadb.discovery.LanAdbScanner
import com.sky22333.skyadb.discovery.NetworkInfoProvider
import com.sky22333.skyadb.download.NetworkDownloadManager
import com.sky22333.skyadb.fastboot.AndroidFastbootRepository
import com.sky22333.skyadb.files.LocalFileManager
import com.sky22333.skyadb.localapps.LocalAppExporter
import com.sky22333.skyadb.repository.DefaultAdbRepository
import com.sky22333.skyadb.usb.AndroidUsbAdbRepository

object AppServices {
    private var appContext: Context? = null

    val kadbManager: KadbManager by lazy { KadbManager() }
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
    val fastbootRepository: AndroidFastbootRepository by lazy {
        AndroidFastbootRepository(requireNotNull(appContext) { "AppServices 尚未初始化 Context" })
    }
    val usbAdbRepository: AndroidUsbAdbRepository by lazy {
        AndroidUsbAdbRepository(requireNotNull(appContext) { "AppServices not initialized" }, adbRepository)
    }
    val adbRepository: DefaultAdbRepository by lazy {
        DefaultAdbRepository(kadbManager, recentDeviceStore, settingsStore)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
