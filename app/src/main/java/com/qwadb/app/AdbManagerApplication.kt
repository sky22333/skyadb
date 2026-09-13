package com.qwadb.app

import android.app.Application
import com.qwadb.app.adb.AdbIdentityManager
import timber.log.Timber

class AdbManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AdbIdentityManager.initialize(this)
        AppServices.initialize(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
