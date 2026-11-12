@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.edgereach.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nihal.edgereach.data.Action
import dev.nihal.edgereach.data.ActionType

@Composable
fun EdgeReachTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A screen with a collapsing title and a scrolling list of grouped cards. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val bg = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = bg,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, scrolledContainerColor = bg),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp),
    )
}

@Composable
fun IconBadge(icon: ImageVector, container: Color = MaterialTheme.colorScheme.secondaryContainer, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Icon for an action: the app's own icon for "Open app", otherwise a tonal glyph. */
@Composable
fun ActionBadge(encoded: String) {
    val ctx = LocalContext.current
    val a = Action.parse(encoded)
    val cmp = a.component
    if (a.type == ActionType.LAUNCH_APP && cmp != null) {
        val bmp = remember(encoded) { AppsRepo.icon(ctx, cmp) }
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(40.dp))
            return
        }
    }
    if (a.type == ActionType.NONE) {
        IconBadge(a.type.icon(), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.outline)
    } else {
        IconBadge(a.type.icon())
    }
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = leading ?: icon?.let { { IconBadge(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
fun SwitchRow(title: String, summary: String?, icon: ImageVector?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, summary, icon, onClick = { onChange(!checked) }, trailing = { Switch(checked, onCheckedChange = onChange) })
}

@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    summary: String? = null,
    onChange: (Float) -> Unit,
    onDone: () -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onDone,
        )
    }
}

@Composable
fun TipCard(
    icon: ImageVector,
    title: String,
    body: String,
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = container, contentColor = content) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (actions != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}

@Composable
fun TipButton(text: String, onClick: () -> Unit) = FilledTonalButton(onClick = onClick) { Text(text) }

// ---- system helpers ----

fun isGestureNav(ctx: Context): Boolean =
    runCatching { Settings.Secure.getInt(ctx.contentResolver, "navigation_mode") == 2 }.getOrDefault(false)

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

fun Context.startSafely(vararg intents: Intent) {
    for (i in intents) {
        if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
    Toast.makeText(this, "Couldn't open settings", Toast.LENGTH_SHORT).show()
}

private fun openAccessibilitySettings(ctx: Context) = ctx.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

const val ACCESSIBILITY_DISCLOSURE =
    "EdgeReach uses Android's Accessibility Service API to:\n\n" +
        "• draw the gesture handles on the edges of your screen\n" +
        "• carry out the actions you pick, such as Back, Home, Recents, notifications and screenshots\n" +
        "• know which app is open and when the keyboard is showing, so handles can hide in apps you exclude " +
        "and Previous app knows where to go\n\n" +
        "EdgeReach can't read what's on your screen or what you type. It collects no data, has no internet " +
        "access and shares nothing.\n\n" +
        "On the next screen, open Downloaded apps (or Installed apps) › EdgeReach gestures and switch it on."

/**
 * Returns a function that shows the accessibility disclosure, then opens Accessibility settings only if the
 * user agrees. Google Play requires this prominent disclosure before sending people to turn the service on.
 */
@Composable
fun rememberAccessibilityPrompt(): () -> Unit {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            icon = { Icon(Icons.Outlined.AccessibilityNew, contentDescription = null) },
            title = { Text("Allow accessibility access") },
            text = { Text(ACCESSIBILITY_DISCLOSURE) },
            confirmButton = {
                TextButton(onClick = { show = false; openAccessibilitySettings(ctx) }) { Text("Agree and continue") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Not now") } },
        )
    }
    return remember { { show = true } }
}

fun openAppInfo(ctx: Context) = ctx.startSafely(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
)

/** Opens the system list of battery-optimised apps (no special permission needed, unlike the direct prompt). */
fun requestIgnoreBattery(ctx: Context) = ctx.startSafely(
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
)

/** "1.6 (build 7)" */
fun appVersion(ctx: Context): String = runCatching {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    "${info.versionName} (build ${info.longVersionCode})"
}.getOrDefault("unknown")

fun serviceComponent(ctx: Context) = ComponentName(ctx, dev.nihal.edgereach.service.EdgeService::class.java)
