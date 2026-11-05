package dev.nihal.thumbedge

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import dev.nihal.thumbedge.data.BackGestureControl
import dev.nihal.thumbedge.data.DiagLog
import dev.nihal.thumbedge.data.ServiceGuard
import dev.nihal.thumbedge.data.SettingsRepo
import dev.nihal.thumbedge.service.EdgeService
import dev.nihal.thumbedge.service.GuardJob

class App : Application() {
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        SettingsRepo.init(this)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            DiagLog.add("CRASH in ${t.name}: ${Log.getStackTraceString(e).take(4000)}")
            previous?.uncaughtException(t, e)
        }

        DiagLog.add("App process started")
        DiagLog.recordExitReasons(this)
        ServiceGuard.check(this, "app started")
        GuardJob.schedule(this)

        // If the service gets switched off while our process is still alive, react right away.
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false,
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    if (ServiceGuard.enabledInSettings(this@App)) return
                    DiagLog.add("Accessibility service was switched off")
                    BackGestureControl.sync(this@App, SettingsRepo.config.value, serviceAlive = false)
                    main.postDelayed({ ServiceGuard.check(this@App, "switched off") }, 2500)
                }
            },
        )
        if (!EdgeService.running.value) {
            BackGestureControl.sync(this, SettingsRepo.config.value, serviceAlive = false)
        }
    }
}
