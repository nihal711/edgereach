package dev.nihal.thumbedge.service

import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.nihal.thumbedge.data.Action
import dev.nihal.thumbedge.data.ActionType
import dev.nihal.thumbedge.data.Side
import dev.nihal.thumbedge.data.actionLabel
import dev.nihal.thumbedge.ui.icon
import kotlin.math.roundToInt

class IndicatorState {
    var visible by mutableStateOf(false)
    var side by mutableStateOf(Side.RIGHT)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var inward by mutableFloatStateOf(0f)
    var level by mutableIntStateOf(0)
    var action by mutableStateOf(ActionType.NONE.name)
    var dark by mutableStateOf(false)
}

/** Full-screen, touch-transparent overlay that shows what a swipe will do while the thumb is moving. */
class IndicatorOverlay(private val ctx: Context, private val wm: WindowManager) {
    private val state = IndicatorState()
    private val owner = OverlayLifecycleOwner()
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private val labels = HashMap<String, String>()
    private val icons = HashMap<ComponentName, ImageBitmap?>()
    private val goneRunnable = Runnable { if (!state.visible) view?.visibility = View.GONE }

    var dark: Boolean
        get() = state.dark
        set(v) { state.dark = v }

    fun attach() {
        if (view != null) return
        owner.start()
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { Indicator(state, ::label, ::icon) }
            visibility = View.GONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "ThumbEdge indicator"
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) view = v
    }

    fun detach() {
        main.removeCallbacks(goneRunnable)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
        owner.stop()
    }

    fun update(side: Side, x: Float, y: Float, inward: Float, level: Int, action: String) {
        val v = view ?: return
        main.removeCallbacks(goneRunnable)
        if (v.visibility != View.VISIBLE) v.visibility = View.VISIBLE
        state.side = side
        state.x = x
        state.y = y
        state.inward = inward
        state.level = level
        state.action = action
        state.visible = true
    }

    fun hide() {
        state.visible = false
        main.removeCallbacks(goneRunnable)
        main.postDelayed(goneRunnable, 260)
    }

    private fun label(encoded: String) = labels.getOrPut(encoded) { actionLabel(ctx, encoded) }

    private fun icon(cmp: ComponentName): ImageBitmap? = icons.getOrPut(cmp) {
        runCatching { ctx.packageManager.getActivityIcon(cmp).toBitmap(112, 112).asImageBitmap() }.getOrNull()
    }
}

@Composable
private fun Indicator(s: IndicatorState, label: (String) -> String, appIcon: (ComponentName) -> ImageBitmap?) {
    val ctx = LocalContext.current
    val scheme = if (s.dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val shown by animateFloatAsState(if (s.visible) 1f else 0f, tween(if (s.visible) 90 else 200), label = "shown")
    if (shown == 0f && !s.visible) return

    val level = s.level
    val scale by animateFloatAsState(
        when (level) { 0 -> 0.72f; 1 -> 1f; else -> 1.18f },
        spring(dampingRatio = 0.55f, stiffness = 700f), label = "scale",
    )
    val container by animateColorAsState(
        when (level) { 0 -> scheme.surfaceContainerHighest; 1 -> scheme.primaryContainer; else -> scheme.primary },
        label = "container",
    )
    val onContainer by animateColorAsState(
        when (level) { 0 -> scheme.onSurfaceVariant; 1 -> scheme.onPrimaryContainer; else -> scheme.onPrimary },
        label = "content",
    )
    val action = Action.parse(s.action)
    val text = remember(s.action) { label(s.action) }
    val density = LocalDensity.current
    val inset = with(density) { 52.dp.toPx() }
    val lift = with(density) { 72.dp.toPx() }
    val maxPullPx = with(density) { 56.dp.toPx() }
    val edgePad = with(density) { 8.dp.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        // Elastic bulge on the screen edge, following the thumb.
        Canvas(Modifier.fillMaxSize()) {
            val left = s.side == Side.LEFT
            val edge = if (left) 0f else size.width
            val sign = if (left) 1f else -1f
            val pull = (s.inward * 0.45f).coerceIn(0f, maxPullPx) * shown
            val cy = s.y
            val hh = 120.dp.toPx()
            val path = Path().apply {
                moveTo(edge, cy - hh)
                cubicTo(edge, cy - hh * 0.4f, edge + sign * pull, cy - hh * 0.3f, edge + sign * pull, cy)
                cubicTo(edge + sign * pull, cy + hh * 0.3f, edge, cy + hh * 0.4f, edge, cy + hh)
                close()
            }
            drawPath(path, container.copy(alpha = 0.55f * shown))
        }

        Column(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(c.maxWidth, c.maxHeight) {
                        val left = s.side == Side.LEFT
                        val travel = (s.inward * 0.3f).coerceAtMost(48.dp.toPx())
                        val cx = if (left) inset + travel else c.maxWidth - inset - travel
                        val px = (cx - p.width / 2f).roundToInt()
                            .coerceIn(edgePad, (c.maxWidth - p.width - edgePad).coerceAtLeast(edgePad))
                        val py = (s.y - lift - p.height).roundToInt().coerceAtLeast(edgePad)
                        p.place(px, py)
                    }
                }
                .graphicsLayer { alpha = shown },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (level > 0) {
                Text(
                    text,
                    color = scheme.inverseOnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .background(scheme.inverseSurface, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
            }
            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(6.dp, CircleShape)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val cmp = action.component
                val bmp = if (action.type == ActionType.LAUNCH_APP && cmp != null) appIcon(cmp) else null
                if (bmp != null) {
                    Image(bmp, null, Modifier.size(36.dp))
                } else {
                    Icon(action.type.icon(), null, tint = onContainer, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** Minimal lifecycle so Compose can run inside a window owned by a service. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) return
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.DESTROYED
    }
}
