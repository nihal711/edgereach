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
import androidx.compose.runtime.Composable
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
                    summary = "How far to swipe before a gesture counts",
                    onChange = { v ->
                        val s = v.roundToInt()
                        upd { it.copy(shortDistDp = s, longDistDp = max(it.longDistDp, s + 40)) }
                    },
                )
                if (config.longMode == LongMode.DISTANCE) {
                    SliderRow(
                        "Long swipe distance",
                        "${config.longDistDp} dp",
                        config.longDistDp.toFloat(), (config.shortDistDp + 40f)..400f,
                        onChange = { v -> upd { it.copy(longDistDp = v.roundToInt()) } },
                    )
                } else {
                    SliderRow(
                        "Hold time",
                        "${config.holdMs} ms",
                        config.holdMs.toFloat(), 200f..1200f,
                        onChange = { v -> upd { it.copy(holdMs = (v / 50f).roundToInt() * 50) } },
                    )
                }
                SliderRow(
                    "Straight zone",
                    "±${config.straightAngle}°",
                    config.straightAngle.toFloat(), 10f..60f,
                    summary = "Swipes steeper than this count as diagonal",
                    onChange = { v -> upd { it.copy(straightAngle = v.roundToInt()) } },
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
