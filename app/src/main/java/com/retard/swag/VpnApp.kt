package com.retard.swag

import android.app.Application
import com.retard.swag.service.XrayManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VpnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The second parameter is an XUDP base key, can be empty for now.
        XrayManager.init(this)
    }
}
