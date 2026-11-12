@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.edgereach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nihal.edgereach.Route
import dev.nihal.edgereach.data.Config
import dev.nihal.edgereach.data.HandleConfig
import dev.nihal.edgereach.data.LongMode
import dev.nihal.edgereach.data.SettingsRepo
import dev.nihal.edgereach.data.Side
import dev.nihal.edgereach.service.EdgeService

@Composable
fun HomeScreen(config: Config, go: (Route) -> Unit) {
    val ctx = LocalContext.current
    val running by EdgeService.running.collectAsStateWithLifecycle()
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    LifecycleResumeEffect(Unit) {
        batteryOk = isIgnoringBattery(ctx)
        onPauseOrDispose { }
    }

    ScreenScaffold(title = "EdgeReach", onBack = null) {
        item {
            if (running) MasterCard(config) else ServiceOffCard { go(Route.FIX) }
        }

        item { SectionTitle("Handles") }
        item {
            GroupCard {
                Side.entries.forEach { side -> HandleRow(side, config, go) }
            }
        }

        item { SectionTitle("Settings") }
        item {
            GroupCard {
                SettingRow(
                    "Gestures",
                    summary = buildString {
                        append(if (config.longMode == LongMode.DISTANCE) "Long swipe" else "Swipe and hold")
                        append(" · ")
                        append(if (config.vibrate) "vibration on" else "vibration off")
                    },
                    icon = Icons.Outlined.Swipe,
                    onClick = { go(Route.GESTURES) },
                )
                SettingRow(
                    "Visibility",
                    summary = if (config.excluded.isEmpty()) "Where handles hide" else "${config.excluded.size} excluded apps",
                    icon = Icons.Outlined.Visibility,
                    onClick = { go(Route.VISIBILITY) },
                )
                SettingRow(
                    "Fix problems",
                    summary = "Turning itself off, back swipe overlap, activity log",
                    icon = Icons.Outlined.Build,
                    onClick = { go(Route.FIX) },
                )
            }
        }

        if (!batteryOk) {
            item {
                TipCard(
                    Icons.Outlined.BatteryAlert,
                    "Keep EdgeReach running",
                    "Many phones pause apps in the background, which makes the handles disappear. Allow EdgeReach to run without battery limits: open the list, choose All apps, find EdgeReach and pick Don't optimise.",
                ) {
                    TipButton("Open battery settings") { requestIgnoreBattery(ctx) }
                }
            }
        }

        item {
            Text(
                "Swipe in from a handle. Release to run the action, or slide back to the edge to cancel.\n\n" +
                    "EdgeReach ${appVersion(ctx)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MasterCard(config: Config) {
    val on = config.enabled
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright,
        onClick = { SettingsRepo.update { it.copy(enabled = !it.enabled) } },
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) "On" else "Paused",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (on) "Edge handles are active" else "Handles are hidden until you turn them back on",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = on, onCheckedChange = { v -> SettingsRepo.update { it.copy(enabled = v) } })
        }
    }
}

@Composable
private fun ServiceOffCard(openFix: () -> Unit) {
    val ctx = LocalContext.current
    val askAccessibility = rememberAccessibilityPrompt()
    TipCard(
        Icons.Outlined.AccessibilityNew,
        "Turn on EdgeReach",
        "EdgeReach needs its accessibility service to draw the handles and press Back, Home and Recents for you.\n\n" +
            "Open Accessibility, tap Downloaded apps (or Installed apps) › EdgeReach gestures, and switch it on.",
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Button(onClick = askAccessibility) { Text("Open Accessibility") }
        TextButton(onClick = openFix) { Text("Keeps turning off?") }
    }
    Spacer(Modifier.height(12.dp))
    TipCard(
        Icons.Outlined.AccessibilityNew,
        "Is the switch greyed out?",
        "Android blocks accessibility for apps installed outside the store until you allow it. Open App info, tap ⋮ in the top corner, choose \"Allow restricted settings\", then try again.",
    ) {
        TextButton(onClick = { openAppInfo(ctx) }) { Text("Open App info") }
    }
}

@Composable
private fun HandleRow(side: Side, config: Config, go: (Route) -> Unit) {
    val hc = config.handle(side)
    SettingRow(
        side.title,
        summary = if (hc.enabled) "${hc.assignedCount()} gestures set" else "Off",
        leading = { HandleGlyph(side, hc, config.enabled) },
        onClick = { go(if (side == Side.LEFT) Route.LEFT else Route.RIGHT) },
        trailing = {
            Switch(
                checked = hc.enabled,
                onCheckedChange = { v -> SettingsRepo.update { c -> c.withHandle(side, c.handle(side).copy(enabled = v)) } },
            )
        },
    )
}

@Composable
private fun HandleGlyph(side: Side, hc: HandleConfig, masterOn: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val color = hc.color?.let { Color(it) } ?: accent
    Box(
        Modifier.size(width = 40.dp, height = 40.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = if (side == Side.LEFT) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier.width(5.dp).fillMaxHeight(0.55f)
                .clip(RoundedCornerShape(3.dp))
                .background(if (hc.enabled && masterOn) color else MaterialTheme.colorScheme.outlineVariant),
        )
    }
}
