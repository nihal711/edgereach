package dev.nihal.thumbedge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import dev.nihal.thumbedge.MainActivity
import dev.nihal.thumbedge.R
import dev.nihal.thumbedge.data.BackGestureControl
import dev.nihal.thumbedge.data.Config
import dev.nihal.thumbedge.data.DiagLog
import dev.nihal.thumbedge.data.PreviewBus
import dev.nihal.thumbedge.data.SettingsRepo
import dev.nihal.thumbedge.data.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.EnumMap

class EdgeService : AccessibilityService(), HandleHost {

    companion object {
        private const val CHANNEL = "keepalive"
        private const val NOTIFICATION_ID = 1
        private val runningState = MutableStateFlow(false)
        @Volatile private var current: EdgeService? = null
        val running: StateFlow<Boolean> = runningState
    }

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var wm: WindowManager? = null
    private val handles = EnumMap<Side, HandleWindow>(Side::class.java)
    private var runner: ActionRunner? = null
    private var highlight: Side? = null

    override var cfg: Config = Config()
        private set
    override var indicator: IndicatorOverlay? = null
        private set

    // What's on screen right now.
    private var foregroundPkg: String? = null
    private val appHistory = ArrayDeque<String>()
    private var imeVisible = false
    private var imeTitle = "keyboard"
    private var screenOn = true
    private var homePackages: Set<String> = emptySet()
    private var replayToken = 0
    private var connected = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
            syncHandles()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SettingsRepo.init(this)
        DiagLog.init(this)
        DiagLog.add("Service connected")
        val w = getSystemService(WindowManager::class.java)
        wm = w
        runner = ActionRunner(this)
        homePackages = resolveHomePackages()
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        indicator = IndicatorOverlay(this, w).also {
            it.dark = isDark()
            it.attach()
        }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        s.launch {
            SettingsRepo.config.collect {
                cfg = it
                syncHandles()
                updateKeepAlive()
                BackGestureControl.sync(this@EdgeService, it, serviceAlive = true)
            }
        }
        s.launch {
            PreviewBus.highlight.collect { side ->
                highlight = side
                syncHandles()
            }
        }
        connected = true
        current = this
        runningState.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (runCatching { event?.eventType }.getOrNull()) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                main.removeCallbacks(envUpdate)
                main.postDelayed(envUpdate, 60)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        indicator?.dark = isDark()
        // Wait a frame so window metrics reflect the new rotation.
        main.post { syncHandles() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagLog.add("Service unbound (switched off in Accessibility, or stopped by the system)")
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!connected) return
        connected = false
        // An old instance can finish shutting down after a new one has connected; only the
        // current instance may report the service as stopped.
        if (current === this) {
            current = null
            runningState.value = false
            BackGestureControl.sync(this, cfg, serviceAlive = false)
        }
        if (foreground) runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        foreground = false
        scope?.cancel(); scope = null
        main.removeCallbacksAndMessages(null)
        handles.values.forEach { it.remove() }
        handles.clear()
        indicator?.detach(); indicator = null
        runner?.release(); runner = null
        runCatching { unregisterReceiver(screenReceiver) }
    }

    // ---- handles ----

    private fun syncHandles() {
        try {
            syncHandlesInner()
        } catch (t: Throwable) {
            DiagLog.add("Handle update failed: $t")
        }
    }

    private fun syncHandlesInner() {
        val w = wm ?: return
        val c = cfg
        val bounds = screenBounds(w)
        val reason = hideReason()
        val show = reason == null
        if (reason != lastHideReason) {
            DiagLog.add(if (reason == null) "Handles shown (app: ${foregroundPkg ?: "?"})" else "Handles hidden: $reason")
            lastHideReason = reason
        }
        for (side in Side.entries) {
            val hc = c.handle(side)
            if (!c.enabled || !hc.enabled) {
                handles.remove(side)?.remove()
                continue
            }
            val win = handles.getOrPut(side) { HandleWindow(this, w, side, this).also { it.add() } }
            win.apply(hc, hc.color ?: accentColor(), bounds, show || highlight == side)
            win.view.highlighted = highlight == side
        }
    }

    private var lastHideReason: String? = "starting"

    /** Why the handles should be hidden right now, or null if they should show. */
    private fun hideReason(): String? {
        val c = cfg
        if (!c.enabled) return "paused"
        if (!screenOn) return "screen off"
        if (c.hideLandscape && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return "landscape"
        if (c.hideKeyboard && imeVisible) return "keyboard open ($imeTitle)"
        if (!c.showOnLockscreen && getSystemService(KeyguardManager::class.java).isKeyguardLocked) return "lock screen"
        val fg = foregroundPkg
        if (fg != null && fg in c.excluded) return "excluded app $fg"
        return null
    }

    override fun onWindowShownChanged(side: Side, shown: Boolean) {
        DiagLog.add(
            if (shown) "System showed the ${side.name.lowercase()} handle again"
            else "System hid the ${side.name.lowercase()} handle (app: ${foregroundPkg ?: "?"})",
        )
    }

    private fun screenBounds(w: WindowManager): Rect = runCatching { w.currentWindowMetrics.bounds }
        .getOrElse { Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) }

    private fun accentColor(): Int = getColor(android.R.color.system_accent1_400)

    private fun isDark() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // ---- keep-alive notification ----

    private var foreground = false

    private fun updateKeepAlive() {
        if (cfg.keepAlive && !foreground) {
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Running in background", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "Keeps OxygenOS from closing the edge handles. You can hide this channel."
                        setShowBadge(false)
                    },
                )
                val open = PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                val n = Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle("Edge handles are on")
                    .setContentText("This keeps OxygenOS from closing Thumb Edge")
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, n)
                }
                foreground = true
                DiagLog.add("Keep-alive notification on")
            } catch (t: Throwable) {
                DiagLog.add("Keep-alive notification failed: $t")
            }
        } else if (!cfg.keepAlive && foreground) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            foreground = false
            DiagLog.add("Keep-alive notification off")
        }
    }

    // ---- foreground app / keyboard tracking ----

    private val envUpdate = Runnable {
        try {
            updateEnvironment()
        } catch (t: Throwable) {
            DiagLog.add("Window update failed: $t")
        }
    }

    private fun updateEnvironment() {
        val ws = runCatching { windows }.getOrNull() ?: return
        // Only count a keyboard that is actually on screen and keyboard-sized; some systems keep a
        // zero-size or tiny IME window around while the keyboard is closed.
        val minImePx = 80 * resources.displayMetrics.density
        val ime = ws.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                Rect().also { r -> it.getBoundsInScreen(r) }.let { r -> r.height() >= minImePx && r.width() > 0 }
        }
        imeVisible = ime != null
        imeTitle = ime?.title?.toString() ?: "keyboard"
        val app = ws.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode }
            .maxByOrNull { if (it.isFocused) Int.MAX_VALUE else it.layer }
        val pkg = runCatching { app?.root?.packageName?.toString() }.getOrNull()
        if (pkg != null && pkg != foregroundPkg) {
            foregroundPkg = pkg
            if (pkg !in homePackages) {
                appHistory.remove(pkg)
                appHistory.addFirst(pkg)
                while (appHistory.size > 10) appHistory.removeLast()
            }
        }
        syncHandles()
    }

    fun previousAppPackage(): String? {
        val fg = foregroundPkg
        return appHistory.firstOrNull { it != fg }
    }

    private fun resolveHomePackages(): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }.toSet()
    }

    // ---- HandleHost ----

    override fun perform(side: Side, action: String) {
        runner?.run(action)
    }

    /** Re-sends a tap or drag that started on a handle to whatever is underneath it. */
    override fun replay(side: Side, xs: FloatArray, ys: FloatArray, count: Int, durationMs: Long) {
        val win = handles[side] ?: return
        val w = wm ?: return
        if (count <= 0) return
        val b = screenBounds(w)
        val maxX = (b.width() - 1).toFloat()
        val maxY = (b.height() - 1).toFloat()
        val path = Path()
        path.moveTo(xs[0].coerceIn(0f, maxX), ys[0].coerceIn(0f, maxY))
        for (i in 1 until count) path.lineTo(xs[i].coerceIn(0f, maxX), ys[i].coerceIn(0f, maxY))
        val duration = durationMs.coerceIn(40L, 4000L)
        val token = ++replayToken
        val restore = Runnable { if (token == replayToken) win.setTouchable(true) }

        win.setTouchable(false)
        main.postDelayed({
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { main.post(restore) }
                override fun onCancelled(gestureDescription: GestureDescription?) { main.post(restore) }
            }, main)
            if (!ok) restore.run()
        }, 48)
        main.postDelayed(restore, duration + 1500)
    }
}
