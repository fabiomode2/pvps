package dev.pvps

import android.app.Application
import dev.pvps.service.AppNotifications

class PvpsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppNotifications.init(this)
    }
}
