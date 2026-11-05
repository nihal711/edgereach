@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.thumbedge.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.nihal.thumbedge.data.Config
import dev.nihal.thumbedge.data.Direction
import dev.nihal.thumbedge.data.HANDLE_COLORS
import dev.nihal.thumbedge.data.HandleConfig
import dev.nihal.thumbedge.data.Length
import dev.nihal.thumbedge.data.PreviewBus
import dev.nihal.thumbedge.data.SettingsRepo
import dev.nihal.thumbedge.data.Side
import dev.nihal.thumbedge.data.actionLabel
import dev.nihal.thumbedge.data.lengthTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HandleScreen(side: Side, config: Config, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val hc = config.handle(side)
    var picking by remember { mutableStateOf<Pair<Direction, Length>?>(null) }
    val scope = rememberCoroutineScope()
    var clearJob by remember { mutableStateOf<Job?>(null) }
    val gestureNav = remember { isGestureNav(ctx) }
    val screenHdp = LocalConfiguration.current.screenHeightDp

    fun edit(f: (HandleConfig) -> HandleConfig) = SettingsRepo.update { it.withHandle(side, f(it.handle(side))) }
    fun previewing() {
        clearJob?.cancel()
        PreviewBus.highlight.value = side
    }
    fun previewDone() {
        clearJob?.cancel()
        clearJob = scope.launch { delay(900); PreviewBus.highlight.value = null }
    }
    DisposableEffect(Unit) { onDispose { PreviewBus.highlight.value = null } }

    ScreenScaffold(
        title = side.title,
        onBack = onBack,
        actions = {
            Switch(hc.enabled, onCheckedChange = { v -> edit { it.copy(enabled = v) } }, Modifier.padding(end = 16.dp))
        },
    ) {
        item { GroupCard { PhonePreview(config, side) } }

        if (gestureNav && hc.enabled && screenHdp * hc.height > 200f) {
            item {
                TipCard(
                    Icons.Outlined.Info,
                    "Overlaps the back gesture",
                    "With gesture navigation, Android lets apps claim at most 200 dp of each edge from the system back swipe. " +
                        "This handle is about ${(screenHdp * hc.height).roundToInt()} dp tall, so the system may take swipes on part of it. " +
                        "Make it shorter, or switch to buttons in Settings › System › Navigation.",
                )
            }
        }

        item { SectionTitle("Gestures") }
        Direction.entries.forEach { d ->
            item(key = d.name) {
                GroupCard {
                    Row(
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(directionIcon(side, d), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(d.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Length.entries.forEach { l ->
                        val action = hc.action(d, l)
                        SettingRow(
                            lengthTitle(l, config.longMode),
                            summary = actionLabel(ctx, action),
                            leading = { ActionBadge(action) },
                            onClick = { picking = d to l },
                            trailing = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }

        item { SectionTitle("Appearance") }
        item {
            GroupCard {
                SliderRow(
                    "Position",
                    "${(hc.position * 100).roundToInt()}% from top",
                    hc.position, 0.05f..0.95f,
                    onChange = { v -> previewing(); edit { it.copy(position = v) } },
                    onDone = ::previewDone,
                )
                SliderRow(
                    "Height",
                    "${(hc.height * 100).roundToInt()}% · ${(screenHdp * hc.height).roundToInt()} dp",
                    hc.height, 0.08f..0.8f,
                    onChange = { v -> previewing(); edit { it.copy(height = v) } },
                    onDone = ::previewDone,
                )
                SliderRow(
                    "Touch width",
                    "${hc.widthDp} dp",
                    hc.widthDp.toFloat(), 6f..48f,
                    summary = "Wider handles are easier to hit but cover more of the app",
                    onChange = { v -> previewing(); edit { it.copy(widthDp = v.roundToInt()) } },
                    onDone = ::previewDone,
                )
                SliderRow(
                    "Visibility",
                    if (hc.opacity < 0.01f) "Invisible" else "${(hc.opacity * 100).roundToInt()}%",
                    hc.opacity, 0f..1f,
                    onChange = { v -> edit { it.copy(opacity = if (v < 0.02f) 0f else v) } },
                )
                Text(
                    "Colour",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                )
                ColorRow(hc.color) { c -> edit { it.copy(color = c) } }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    SettingsRepo.update { c ->
                        c.withHandle(side.other, c.handle(side).copy(enabled = c.handle(side.other).enabled))
                    }
                    Toast.makeText(ctx, "Copied to the ${side.other.title.lowercase()}", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy these settings to the ${side.other.title.lowercase()}")
            }
        }
    }

    picking?.let { (d, l) ->
        ActionPickerSheet(
            title = "${d.title} · ${lengthTitle(l, config.longMode).lowercase()}",
            current = hc.action(d, l),
            onPick = { a ->
                edit { it.withAction(d, l, a) }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun ColorRow(selected: Int?, onPick: (Int?) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HANDLE_COLORS.forEach { c ->
            val color = c?.let { Color(it) } ?: accent
            val isSel = c == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(color)
                    .border(
                        if (isSel) 3.dp else 1.dp,
                        if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (color.luminance() > 0.5f) Color.Black else Color.White
                when {
                    isSel -> Icon(Icons.Filled.Check, contentDescription = "Selected", tint = tint, modifier = Modifier.size(20.dp))
                    c == null -> Icon(Icons.Filled.AutoAwesome, contentDescription = "System colour", tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A small phone outline showing where both handles sit. */
@Composable
fun PhonePreview(config: Config, focus: Side?) {
    val accent = MaterialTheme.colorScheme.primary
    val frame = MaterialTheme.colorScheme.outline
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.width(128.dp).aspectRatio(9f / 19.8f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(3.dp, frame, RoundedCornerShape(22.dp)),
        ) {
            val h = maxHeight
            val w = maxWidth
            // Fake app content
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(7) { i ->
                    Box(
                        Modifier.fillMaxWidth(if (i % 3 == 2) 0.6f else 1f).height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                }
            }
            if (config.enabled) {
                Side.entries.forEach { side ->
                    val hc = config.handle(side)
                    if (!hc.enabled) return@forEach
                    val hh = h * hc.height
                    val top = (h * hc.position - hh / 2).coerceIn(0.dp, h - hh)
                    val ww = (hc.widthDp.dp * (w / 412.dp)).coerceAtLeast(3.dp)
                    val base = hc.color?.let { Color(it) } ?: accent
                    val alpha = if (focus == null || focus == side) hc.opacity.coerceAtLeast(0.25f) else 0.15f
                    val shape = if (side == Side.LEFT) RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
                    else RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                    Box(
                        Modifier
                            .align(if (side == Side.LEFT) Alignment.TopStart else Alignment.TopEnd)
                            .offset(y = top)
                            .width(ww).height(hh)
                            .clip(shape)
                            .background(base.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
