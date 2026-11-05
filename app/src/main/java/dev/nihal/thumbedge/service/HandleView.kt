package dev.nihal.thumbedge.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.nihal.thumbedge.data.Action
import dev.nihal.thumbedge.data.ActionType
import dev.nihal.thumbedge.data.Config
import dev.nihal.thumbedge.data.Direction
import dev.nihal.thumbedge.data.HandleConfig
import dev.nihal.thumbedge.data.Length
import dev.nihal.thumbedge.data.LongMode
import dev.nihal.thumbedge.data.Side
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

interface HandleHost {
    val cfg: Config
    val indicator: IndicatorOverlay?
    fun perform(side: Side, action: String)
    fun replay(side: Side, xs: FloatArray, ys: FloatArray, count: Int, durationMs: Long)
    /** The system hid or re-showed a handle window that we wanted visible. */
    fun onWindowShownChanged(side: Side, shown: Boolean)
}

/**
 * One edge handle. Recognises three swipe directions (straight, diagonally up, diagonally down),
 * each as a short or long gesture. "Long" is either a longer swipe or a swipe-and-hold.
 * The action fires on release, so moving back to the edge cancels it.
 */
@SuppressLint("ViewConstructor")
class HandleView(ctx: Context, val side: Side, private val host: HandleHost) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var color = 0
    private var opacity = 0f

    var highlighted = false
        set(v) { if (field != v) { field = v; invalidate() } }

    fun style(color: Int, opacity: Float) {
        if (this.color != color || this.opacity != opacity) {
            this.color = color
            this.opacity = opacity
            invalidate()
        }
    }

    /**
     * How much of the handle (from the bottom) the system keeps free of its back swipe. Android
     * allows at most 200 dp per edge; above that the system back gesture wins, so that part is
     * drawn dimmer.
     */
    var protectedPx: Int = Int.MAX_VALUE
        set(v) { if (field != v) { field = v; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val a = if (highlighted) max(opacity, 0.85f) else opacity
        if (a <= 0.005f) return
        paint.color = color
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w
        // Flush with the screen edge, rounded on the inner side.
        if (side == Side.LEFT) rect.set(-r, 0f, w, h) else rect.set(0f, 0f, w + r, h)
        val split = (h - protectedPx).coerceIn(0f, h)
        if (split > 0f) {
            canvas.save()
            canvas.clipRect(0f, 0f, w, split)
            paint.alpha = (a * 0.35f * 255).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(rect, r, r, paint)
            canvas.restore()
        }
        canvas.save()
        canvas.clipRect(0f, split, w, h)
        paint.alpha = (a * 255).roundToInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, r, r, paint)
        canvas.restore()
    }

    fun setExclusion(on: Boolean) {
        systemGestureExclusionRects = if (on && width > 0) listOf(Rect(0, 0, width, height)) else emptyList()
    }

    private var systemHidden = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Android caps an app's back-swipe exclusion at 200 dp per edge, except for windows that
        // ask for the navigation bar to be hidden in swipe-to-reveal mode. This window can't
        // focus, so it never actually hides the bar, but the request lifts the cap so the whole
        // handle stays free of the system back swipe.
        runCatching {
            windowInsetsController?.let {
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(android.view.WindowInsets.Type.navigationBars())
            }
        }
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        // Our own hiding sets the view INVISIBLE; anything else means the system hid the window.
        val hiddenBySystem = v != VISIBLE && visibility == VISIBLE
        if (hiddenBySystem != systemHidden) {
            systemHidden = hiddenBySystem
            host.onWindowShownChanged(side, !hiddenBySystem)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setExclusion(visibility == VISIBLE)
    }

    // ---- gesture tracking ----

    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var level = 0
    private var maxLevel = 0
    private var dir = Direction.STRAIGHT
    private var holdReached = false
    private var holdAnchorX = 0f
    private var holdAnchorY = 0f
    private var indicatorShown = false
    private val xs = FloatArray(MAX_POINTS)
    private val ys = FloatArray(MAX_POINTS)
    private var count = 0

    private val holdRunnable = Runnable {
        if (tracking && level > 0) {
            holdReached = true
            evaluate(lastX, lastY)
        }
    }

    private fun addPoint(x: Float, y: Float) {
        if (count > 0 && hypot(x - xs[count - 1], y - ys[count - 1]) < 2f) return
        val i = if (count < MAX_POINTS) count++ else MAX_POINTS - 1
        xs[i] = x
        ys[i] = y
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = true
                downX = e.rawX; downY = e.rawY; downTime = e.eventTime
                lastX = downX; lastY = downY
                level = 0; maxLevel = 0; dir = Direction.STRAIGHT
                holdReached = false; indicatorShown = false
                count = 0
                addPoint(downX, downY)
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                lastX = e.rawX; lastY = e.rawY
                addPoint(lastX, lastY)
                try {
                    evaluate(lastX, lastY)
                } catch (t: Throwable) {
                    dev.nihal.thumbedge.data.DiagLog.add("Gesture error: $t")
                }
            }
            MotionEvent.ACTION_UP -> if (tracking) {
                tracking = false
                addPoint(e.rawX, e.rawY)
                finish(commit = true, upTime = e.eventTime)
            }
            MotionEvent.ACTION_CANCEL -> if (tracking) {
                tracking = false
                finish(commit = false, upTime = e.eventTime)
            }
        }
        return true
    }

    private fun evaluate(x: Float, y: Float) {
        val c = host.cfg
        val hc = c.handle(side)
        val inward = if (side == Side.LEFT) x - downX else downX - x
        val dy = y - downY
        val dist = hypot(inward, dy)
        val shortPx = c.shortDistDp * density
        val longPx = c.longDistDp * density
        val angle = Math.toDegrees(atan2(-dy.toDouble(), inward.toDouble())).toFloat()
        val inCone = inward > 0f && abs(angle) <= 80f
        val newDir = when {
            angle > c.straightAngle -> Direction.UP
            angle < -c.straightAngle -> Direction.DOWN
            else -> Direction.STRAIGHT
        }
        // Slight hysteresis so the gesture doesn't flicker at the threshold.
        val armAt = if (level > 0) shortPx * 0.75f else shortPx
        var newLevel = if (inCone && dist >= armAt) 1 else 0

        if (newLevel == 1) {
            val hasLong = Action.parse(hc.action(newDir, Length.LONG)).type != ActionType.NONE
            when (c.longMode) {
                LongMode.DISTANCE -> if (hasLong && dist >= longPx) newLevel = 2
                LongMode.HOLD -> {
                    if (level == 0 || hypot(x - holdAnchorX, y - holdAnchorY) > 12 * density) {
                        holdAnchorX = x; holdAnchorY = y
                        if (!holdReached) {
                            removeCallbacks(holdRunnable)
                            postDelayed(holdRunnable, c.holdMs.toLong())
                        }
                    }
                    if (hasLong && holdReached) newLevel = 2
                }
            }
        } else {
            removeCallbacks(holdRunnable)
            holdReached = false
        }

        if (newLevel != level || (newLevel > 0 && newDir != dir)) {
            if (c.vibrate) haptic(level, newLevel, newDir != dir)
            level = newLevel
            dir = newDir
            maxLevel = max(maxLevel, level)
        } else {
            dir = newDir
        }

        val ind = host.indicator
        if (c.showIndicator && ind != null) {
            if (!indicatorShown && inward > 8 * density) indicatorShown = true
            if (indicatorShown) {
                val len = if (level == 2) Length.LONG else Length.SHORT
                ind.update(side, x, y, max(0f, inward), level, hc.action(dir, len))
            }
        }
    }

    private fun haptic(old: Int, new: Int, dirChanged: Boolean) {
        val constant = when {
            new > old && new == 2 -> HapticFeedbackConstants.VIRTUAL_KEY
            new > old -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
            new < old -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE else HapticFeedbackConstants.CLOCK_TICK
            dirChanged -> HapticFeedbackConstants.CLOCK_TICK
            else -> return
        }
        @Suppress("DEPRECATION")
        performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    private fun finish(commit: Boolean, upTime: Long) {
        removeCallbacks(holdRunnable)
        if (indicatorShown) host.indicator?.hide()
        indicatorShown = false
        val c = host.cfg
        if (commit) {
            if (level > 0) {
                val a = c.handle(side).action(dir, if (level == 2) Length.LONG else Length.SHORT)
                if (a != ActionType.NONE.name) host.perform(side, a)
            } else if (maxLevel == 0 && c.passThrough) {
                host.replay(side, xs, ys, count, upTime - downTime)
            }
        }
        level = 0
    }

    companion object {
        private const val MAX_POINTS = 96
    }
}

/** The overlay window that holds a [HandleView]. */
class HandleWindow(ctx: Context, private val wm: WindowManager, val side: Side, host: HandleHost) {
    val view = HandleView(ctx, side, host)
    private val density = ctx.resources.displayMetrics.density
    private var attached = false
    private var shown = true
    private var touchable = true

    private val lp = WindowManager.LayoutParams(
        1, 1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or (if (side == Side.LEFT) Gravity.LEFT else Gravity.RIGHT)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "ThumbEdge ${side.name.lowercase()} handle"
    }

    fun add() {
        if (attached) return
        attached = runCatching { wm.addView(view, lp) }.isSuccess
    }

    fun remove() {
        if (!attached) return
        runCatching { wm.removeViewImmediate(view) }
        attached = false
    }

    fun apply(hc: HandleConfig, color: Int, screen: Rect, show: Boolean, protectedPx: Int) {
        view.protectedPx = protectedPx
        val sh = screen.height()
        val w = (hc.widthDp * density).roundToInt().coerceAtLeast(1)
        val h = (hc.height * sh).roundToInt().coerceIn((48 * density).roundToInt(), sh)
        val y = (hc.position * sh - h / 2f).roundToInt().coerceIn(0, max(0, sh - h))
        view.style(color, hc.opacity)
        shown = show
        if (lp.width != w || lp.height != h || lp.y != y) {
            lp.width = w; lp.height = h; lp.y = y; lp.x = 0
            commit(force = true)
        } else {
            commit(force = false)
        }
        view.visibility = if (show) View.VISIBLE else View.INVISIBLE
        view.setExclusion(show)
    }

    fun setTouchable(t: Boolean) {
        touchable = t
        commit(force = false)
    }

    private fun commit(force: Boolean) {
        val flags = BASE_FLAGS or (if (shown && touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        if (!force && lp.flags == flags) return
        lp.flags = flags
        if (attached) runCatching { wm.updateViewLayout(view, lp) }
    }

    companion object {
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
