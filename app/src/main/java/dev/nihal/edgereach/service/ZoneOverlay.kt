package dev.nihal.edgereach.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.nihal.edgereach.data.Config
import dev.nihal.edgereach.data.Side
import dev.nihal.edgereach.data.actionLabel
import dev.nihal.edgereach.ui.ZoneFan
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * Temporary fans drawn next to each handle while the user tunes swipe sensitivity, showing where
 * the up / straight / down zones and the short / long distances are on the real screen.
 *
 * Each fan is its own small, touch-transparent window beside its handle. A single full-screen
 * window would trip OnePlus's anti-fraud check (see IndicatorOverlay).
 */
class ZoneOverlay(private val ctx: Context, private val wm: WindowManager) {
    private val owner = OverlayLifecycleOwner().also { it.start() }
    private val cfg = mutableStateOf(Config())
    private val radiusDp = mutableStateOf(MIN_RADIUS_DP)
    private val views = EnumMap<Side, Pair<ComposeView, WindowManager.LayoutParams>>(Side::class.java)
    private val labels = HashMap<String, String>()
    private val density = ctx.resources.displayMetrics.density

    /** Shows (or updates) a fan for each side in [centers], keyed to that handle's centre Y in px. */
    fun show(config: Config, centers: Map<Side, Int>, screenHeight: Int) {
        cfg.value = config
        val r = (config.longDistDp + 16f).coerceIn(MIN_RADIUS_DP, MAX_RADIUS_DP)
        radiusDp.value = r
        val w = ((r + 8f) * density).roundToInt()
        val h = (2f * r * density).roundToInt()
        for (side in Side.entries) {
            val cy = centers[side]
            if (cy == null) {
                views.remove(side)?.let { (v, _) -> runCatching { wm.removeViewImmediate(v) } }
                continue
            }
            val y = (cy - h / 2).coerceIn(0, (screenHeight - h).coerceAtLeast(0))
            val existing = views[side]
            if (existing == null) {
                val lp = params(side, w, h, y)
                val v = ComposeView(ctx).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setContent {
                        ZoneFan(
                            config = cfg.value,
                            side = side,
                            fanRadius = radiusDp.value.dp,
                            dpScale = 1f,
                            label = ::label,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (runCatching { wm.addView(v, lp) }.isSuccess) views[side] = v to lp
            } else {
                val (v, lp) = existing
                if (lp.y != y || lp.width != w || lp.height != h) {
                    lp.y = y; lp.width = w; lp.height = h
                    runCatching { wm.updateViewLayout(v, lp) }
                }
            }
        }
    }

    fun hide() {
        views.values.forEach { (v, _) -> runCatching { wm.removeViewImmediate(v) } }
        views.clear()
    }

    fun destroy() {
        hide()
        owner.stop()
    }

    private fun label(encoded: String) = labels.getOrPut(encoded) { actionLabel(ctx, encoded) }

    private fun params(side: Side, w: Int, h: Int, y: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or (if (side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT)
        x = 0
        this.y = y
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "EdgeReach zones ${side.name.lowercase()}"
    }

    companion object {
        const val MIN_RADIUS_DP = 150f
        const val MAX_RADIUS_DP = 200f
    }
}
