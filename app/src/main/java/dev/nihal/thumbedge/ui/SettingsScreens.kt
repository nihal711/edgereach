@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.thumbedge.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.nihal.thumbedge.data.PreviewBus
import dev.nihal.thumbedge.data.Side
import dev.nihal.thumbedge.data.actionLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nihal.thumbedge.Route
import dev.nihal.thumbedge.data.Config
import dev.nihal.thumbedge.data.LongMode
import dev.nihal.thumbedge.data.SettingsRepo
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun GestureSettingsScreen(config: Config, onBack: () -> Unit) {
    val upd = SettingsRepo::update
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<Job?>(null) }
    // While a sensitivity slider moves, the service draws the swipe zones next to the real handles.
    fun previewing() {
        hideJob?.cancel()
        PreviewBus.zones.value = true
    }
    fun previewDone() {
        hideJob?.cancel()
        hideJob = scope.launch { delay(1800); PreviewBus.zones.value = false }
    }
    DisposableEffect(Unit) { onDispose { PreviewBus.zones.value = false } }

    ScreenScaffold("Gestures", onBack) {
        item { SectionTitle("Long gesture") }
        item {
            GroupCard {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    LongMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = config.longMode == m,
                            onClick = { upd { it.copy(longMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, LongMode.entries.size),
                        ) {
                            Text(if (m == LongMode.DISTANCE) "Swipe further" else "Swipe and hold")
                        }
                    }
                }
                Text(
                    if (config.longMode == LongMode.DISTANCE) {
                        "Keep swiping past the long-swipe distance to switch to the long action."
                    } else {
                        "Swipe in, then hold your thumb still to switch to the long action."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                SliderRow(
                    "Short swipe distance",
                    "${config.shortDistDp} dp",
                    config.shortDistDp.toFloat(), 16f..120f,
                    summary = "How far to swipe before a gesture counts (dashed ring)",
                    onChange = { v ->
                        previewing()
                        val s = v.roundToInt()
                        upd { it.copy(shortDistDp = s, longDistDp = max(it.longDistDp, s + 40)) }
                    },
                    onDone = ::previewDone,
                )
                if (config.longMode == LongMode.DISTANCE) {
                    SliderRow(
                        "Long swipe distance",
                        "${config.longDistDp} dp",
                        config.longDistDp.toFloat(), (config.shortDistDp + 40f)..400f,
                        summary = "Swipe past this for the long action (solid ring)",
                        onChange = { v -> previewing(); upd { it.copy(longDistDp = v.roundToInt()) } },
                        onDone = ::previewDone,
                    )
                } else {
                    SliderRow(
                        "Hold time",
                        "${config.holdMs} ms",
                        config.holdMs.toFloat(), 200f..1200f,
                        onChange = { v -> upd { it.copy(holdMs = (v / 50f).roundToInt() * 50) } },
                    )
                }
            }
        }

        item { SectionTitle("Direction") }
        item {
            GroupCard {
                Text(
                    "Drag a slider to see the zones on your real handles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Box(
                    Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1F24)),
                ) {
                    ZoneFan(
                        config = config,
                        side = Side.RIGHT,
                        fanRadius = 118.dp,
                        dpScale = 118f / (config.longDistDp + 16f).coerceIn(150f, 200f),
                        label = { actionLabel(ctx, it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SliderRow(
                    "Diagonal up starts at",
                    "${config.upAngle}° above straight",
                    config.upAngle.toFloat(), 10f..70f,
                    summary = "Higher = you must swipe more steeply upward for diagonal up",
                    onChange = { v -> previewing(); upd { it.copy(upAngle = v.roundToInt()) } },
                    onDone = ::previewDone,
                )
                SliderRow(
                    "Diagonal down starts at",
                    "${config.downAngle}° below straight",
                    config.downAngle.toFloat(), 10f..70f,
                    summary = "Raise this if slanted back swipes trigger diagonal down by mistake",
                    onChange = { v -> previewing(); upd { it.copy(downAngle = v.roundToInt()) } },
                    onDone = ::previewDone,
                )
            }
        }

        item { SectionTitle("Feedback") }
        item {
            GroupCard {
                SwitchRow(
                    "Vibration", "A tick when a gesture is ready and when it turns long", Icons.Outlined.Vibration,
                    config.vibrate,
                ) { v -> upd { it.copy(vibrate = v) } }
                SwitchRow(
                    "Gesture preview", "Shows the action's icon and name while you swipe", Icons.Outlined.Visibility,
                    config.showIndicator,
                ) { v -> upd { it.copy(showIndicator = v) } }
                SwitchRow(
                    "Let touches through",
                    "Taps and scrolls that start on a handle are passed to the app underneath",
                    Icons.Outlined.TouchApp,
                    config.passThrough,
                ) { v -> upd { it.copy(passThrough = v) } }
            }
        }
    }
}

@Composable
fun VisibilityScreen(config: Config, onBack: () -> Unit, go: (Route) -> Unit) {
    val upd = SettingsRepo::update
    ScreenScaffold("Visibility", onBack) {
        item {
            GroupCard {
                SwitchRow("Hide in landscape", null, Icons.Outlined.ScreenRotation, config.hideLandscape) { v ->
                    upd { it.copy(hideLandscape = v) }
                }
                SwitchRow("Hide while typing", "When the keyboard is open", Icons.Outlined.Keyboard, config.hideKeyboard) { v ->
                    upd { it.copy(hideKeyboard = v) }
                }
                SwitchRow("Show on lock screen", null, Icons.Outlined.Lock, config.showOnLockscreen) { v ->
                    upd { it.copy(showOnLockscreen = v) }
                }
            }
        }
        item {
            GroupCard {
                SettingRow(
                    "Excluded apps",
                    summary = when (val n = config.excluded.size) {
                        0 -> "Handles hide in apps you pick"
                        1 -> "1 app"
                        else -> "$n apps"
                    },
                    icon = Icons.Outlined.Block,
                    onClick = { go(Route.EXCLUDED) },
                )
            }
        }
    }
}

@Composable
fun ExcludedAppsScreen(config: Config, onBack: () -> Unit) {
    AppListScreen(
        title = "Excluded apps",
        onBack = onBack,
        isChecked = { it.pkg in config.excluded },
        distinctPackages = true,
        onClick = { app ->
            SettingsRepo.update {
                it.copy(excluded = if (app.pkg in it.excluded) it.excluded - app.pkg else it.excluded + app.pkg)
            }
        },
    )
}
