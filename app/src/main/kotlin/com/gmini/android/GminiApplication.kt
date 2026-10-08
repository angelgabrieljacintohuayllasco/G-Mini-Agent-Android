package com.gmini.android

import android.app.Application
import com.gmini.android.di.AppContainer
import com.gmini.android.service.AgentNotifications

class GminiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AgentNotifications.ensureChannels(this)
    }
}
