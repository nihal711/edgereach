package dev.nihal.thumbedge

import android.app.Application
import dev.nihal.thumbedge.data.SettingsRepo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsRepo.init(this)
    }
}
