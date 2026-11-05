@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.thumbedge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nihal.thumbedge.data.Config
import dev.nihal.thumbedge.data.DiagLog
import dev.nihal.thumbedge.data.ServiceGuard
import dev.nihal.thumbedge.data.SettingsRepo
import dev.nihal.thumbedge.service.EdgeService

@Composable
fun FixScreen(config: Config, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val running by EdgeService.running.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val battery = remember(tick) { isIgnoringBattery(ctx) }
    val secure = remember(tick) { ServiceGuard.hasSecurePermission(ctx) }
    val enabled = remember(tick, running) { ServiceGuard.enabledInSettings(ctx) }
    var log by remember(tick) { mutableStateOf(DiagLog.lines()) }
    val upd = SettingsRepo::update

    ScreenScaffold("Fix problems", onBack) {
        item { SectionTitle("Status") }
        item {
            GroupCard {
                StatusRow(
                    "Accessibility service",
                    when {
                        running -> "On and running"
                        enabled -> "Switched on, but not running yet"
                        else -> "Off"
                    },
                    ok = running,
                    action = if (!running) ("Open" to { openAccessibilitySettings(ctx) }) else null,
                )
                StatusRow(
                    "Battery",
                    if (battery) "No battery limits" else "OxygenOS may close the app",
                    ok = battery,
                    action = if (!battery) ("Allow" to { requestIgnoreBattery(ctx) }) else null,
                )
                StatusRow(
                    "Auto-repair permission",
                    if (secure) "Granted" else "Not granted (needs adb once, see below)",
                    ok = secure,
                )
            }
        }

        item { SectionTitle("Stop it turning off") }
        item {
            TipCard(
                Icons.Outlined.BatteryChargingFull,
                "Why it switches itself off",
                "When OxygenOS closes an app in the background, Android also switches off that app's accessibility service. " +
                    "These settings stop OxygenOS closing Thumb Edge:\n\n" +
                    "1. App info › Battery usage › turn on Allow background activity (and Allow auto launch, if shown).\n" +
                    "2. Open Recents, tap the ⋮ (or long-press) on Thumb Edge's card, and choose Lock.\n" +
                    "3. Keep the keep-alive notification below switched on.",
            ) {
                TipButton("Open App info") { openAppInfo(ctx) }
            }
        }
        item {
            GroupCard {
                SwitchRow(
                    "Keep-alive notification",
                    "A silent notification that marks Thumb Edge as running, so OxygenOS leaves it alone. " +
                        "You can hide it by long-pressing the notification.",
                    Icons.Outlined.NotificationsActive,
                    config.keepAlive,
                ) { v -> upd { it.copy(keepAlive = v) } }
                SwitchRow(
                    "Turn back on automatically",
                    if (secure) {
                        "If the service is switched off, Thumb Edge turns it back on when the app starts, when you open Quick Settings, and every 15 minutes"
                    } else {
                        "Needs the one-time adb permission below"
                    },
                    Icons.Outlined.Refresh,
                    config.autoRepair && secure,
                ) { v -> if (secure) upd { it.copy(autoRepair = v) } else toast(ctx, "Grant the adb permission first") }
            }
        }

        item { SectionTitle("Back swipe overlap") }
        item {
            TipCard(
                Icons.Outlined.ErrorOutline,
                "Keep handles 200 dp or shorter",
                "With gesture navigation, Android lets an app keep the system back swipe off only 200 dp of each edge. " +
                    "On a taller handle, the bottom 200 dp goes to Thumb Edge and the dimmer top part goes to the system back swipe. " +
                    "Open a handle and tap \"Fit to 200 dp\", or switch to buttons in Settings › System › Navigation.",
            )
        }
        if (!isOplus()) {
            item {
                GroupCard {
                    SwitchRow(
                        "Turn off system back on handle edges",
                        if (secure) {
                            "Only on edges with a handle, and only while Thumb Edge is running. Restored when you turn this off."
                        } else {
                            "Needs the one-time adb permission below"
                        },
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        config.blockSystemBack && secure,
                    ) { v -> if (secure) upd { it.copy(blockSystemBack = v) } else toast(ctx, "Grant the adb permission first") }
                }
            }
        }

        if (!secure) {
            item { SectionTitle("One-time adb permission") }
            item {
                TipCard(
                    Icons.Outlined.AdminPanelSettings,
                    "Grant from a computer",
                    "Optional. \"Turn back on automatically\" needs a permission Android only gives over USB or wireless debugging. Do this once:\n\n" +
                        "1. Settings › About device › tap Version › tap Build number 7 times.\n" +
                        "2. Settings › System › Developer options › turn on USB debugging (and \"Disable permission monitoring\" if you see it).\n" +
                        "3. Connect to a computer with adb and run the command below. It stays granted until you uninstall.",
                ) {
                    TipButton("Copy command") { copy(ctx, ServiceGuard.ADB_GRANT) }
                }
            }
            item {
                Text(
                    ServiceGuard.ADB_GRANT,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(16.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }

        item { SectionTitle("Activity log") }
        item {
            GroupCard {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(
                        "Newest first. Shows when the service started and stopped, and Android's reason for closing the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedButton(onClick = { copy(ctx, report(ctx, log)) }) { Text("Copy report") }
                        TextButton(onClick = { DiagLog.clear(); log = emptyList() }) { Text("Clear") }
                    }
                    if (log.isEmpty()) {
                        Text("Nothing yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                    log.take(60).forEach { line ->
                        Text(
                            line.take(400),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, summary: String, ok: Boolean, action: Pair<String, () -> Unit>? = null) {
    SettingRow(
        title,
        summary,
        leading = {
            IconBadge(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                container = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                tint = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        },
        trailing = action?.let { (label, onClick) -> { TextButton(onClick = onClick) { Text(label) } } },
    )
}

private fun report(ctx: Context, log: List<String>): String = buildString {
    appendLine("Thumb Edge report")
    val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
    appendLine("App $v · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (${Build.DISPLAY})")
    appendLine("Service running: ${EdgeService.running.value}, enabled: ${ServiceGuard.enabledInSettings(ctx)}")
    appendLine("Battery unrestricted: ${isIgnoringBattery(ctx)}, adb permission: ${ServiceGuard.hasSecurePermission(ctx)}")
    appendLine("Gesture navigation: ${isGestureNav(ctx)}")
    appendLine()
    log.take(120).forEach { appendLine(it) }
}

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Thumb Edge", text))
    if (Build.VERSION.SDK_INT < 33) toast(ctx, "Copied")
}

private fun isOplus() = Build.MANUFACTURER.lowercase() in setOf("oneplus", "oppo", "realme")

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
