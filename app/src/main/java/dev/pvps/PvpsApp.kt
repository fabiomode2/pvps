package dev.pvps

import android.app.Application
import dev.pvps.diagnostics.Diagnostics
import dev.pvps.service.AppNotifications

class PvpsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        container = AppContainer(this)
        AppNotifications.init(this)
    }
}
