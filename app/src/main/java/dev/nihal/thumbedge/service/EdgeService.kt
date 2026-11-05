package dev.nihal.thumbedge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
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
import dev.nihal.thumbedge.data.Config
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
        private val runningState = MutableStateFlow(false)
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
    private var screenOn = true
    private var homePackages: Set<String> = emptySet()
    private var replayToken = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
            syncHandles()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SettingsRepo.init(this)
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
        s.launch { SettingsRepo.config.collect { cfg = it; syncHandles() } }
        s.launch {
            PreviewBus.highlight.collect { side ->
                highlight = side
                syncHandles()
            }
        }
        runningState.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
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
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!runningState.value && scope == null) return
        runningState.value = false
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
        val w = wm ?: return
        val c = cfg
        val bounds = screenBounds(w)
        val show = shouldShow()
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

    private fun shouldShow(): Boolean {
        val c = cfg
        if (!screenOn) return false
        if (c.hideLandscape && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return false
        if (c.hideKeyboard && imeVisible) return false
        if (!c.showOnLockscreen && getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        val fg = foregroundPkg
        return fg == null || fg !in c.excluded
    }

    private fun screenBounds(w: WindowManager): Rect = runCatching { w.currentWindowMetrics.bounds }
        .getOrElse { Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) }

    private fun accentColor(): Int = getColor(android.R.color.system_accent1_400)

    private fun isDark() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // ---- foreground app / keyboard tracking ----

    private val envUpdate = Runnable { updateEnvironment() }

    private fun updateEnvironment() {
        val ws = runCatching { windows }.getOrNull() ?: return
        imeVisible = ws.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        val app = ws.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode }
            .maxByOrNull { if (it.isFocused) Int.MAX_VALUE else it.layer }
        val pkg = app?.root?.packageName?.toString()
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
