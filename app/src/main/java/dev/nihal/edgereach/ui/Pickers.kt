@file:OptIn(ExperimentalMaterial3Api::class)

package dev.nihal.edgereach.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import dev.nihal.edgereach.data.Action
import dev.nihal.edgereach.data.ActionType
import dev.nihal.edgereach.data.Category
import dev.nihal.edgereach.data.actionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val component: ComponentName, val icon: ImageBitmap) {
    val pkg: String get() = component.packageName
}

object AppsRepo {
    @Volatile
    var cached: List<AppEntry>? = null
        private set

    suspend fun load(ctx: Context): List<AppEntry> = cached ?: withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    icon = ri.loadIcon(pm).toBitmap(112, 112).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
        cached = list
        list
    }

    fun icon(ctx: Context, cmp: ComponentName): ImageBitmap? =
        cached?.firstOrNull { it.component == cmp }?.icon
            ?: runCatching { ctx.packageManager.getActivityIcon(cmp).toBitmap(112, 112).asImageBitmap() }.getOrNull()
}

@Composable
fun rememberApps(): List<AppEntry>? {
    val ctx = LocalContext.current
    val apps by produceState(AppsRepo.cached, ctx) { value = AppsRepo.load(ctx) }
    return apps
}

@Composable
fun ActionPickerSheet(title: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pickingApp by remember { mutableStateOf(false) }
    val currentType = Action.parse(current).type
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
            Category.entries.forEach { cat ->
                item(key = "h_${cat.name}") {
                    Text(
                        cat.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(ActionType.entries.filter { it.category == cat }, key = { it.name }) { t ->
                    val selected = currentType == t
                    val shownAction = if (t == ActionType.LAUNCH_APP && selected) current else t.name
                    ListItem(
                        headlineContent = {
                            Text(if (t == ActionType.LAUNCH_APP && selected) actionLabel(ctx, current) else t.label)
                        },
                        supportingContent = t.hint?.let { { Text(it) } },
                        leadingContent = { ActionBadge(shownAction) },
                        trailingContent = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { if (t == ActionType.LAUNCH_APP) pickingApp = true else onPick(t.name) },
                    )
                }
            }
        }
    }

    if (pickingApp) {
        Dialog(
            onDismissRequest = { pickingApp = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AppListScreen(
                title = "Choose an app",
                onBack = { pickingApp = false },
                isChecked = null,
                onClick = { app ->
                    pickingApp = false
                    onPick(Action(ActionType.LAUNCH_APP, app.component).encode())
                },
            )
        }
    }
}

/** Searchable list of launchable apps, used for "Open app" and for excluded apps. */
@Composable
fun AppListScreen(
    title: String,
    onBack: () -> Unit,
    isChecked: ((AppEntry) -> Boolean)?,
    distinctPackages: Boolean = false,
    onClick: (AppEntry) -> Unit,
) {
    val apps = rememberApps()
    var query by rememberSaveable { mutableStateOf("") }
    val bg = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val filtered = remember(apps, query, distinctPackages) {
                val base = if (distinctPackages) apps.distinctBy { it.pkg } else apps
                if (query.isBlank()) base else base.filter { it.label.contains(query.trim(), ignoreCase = true) }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.component.flattenToString() }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = { Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
                        trailingContent = isChecked?.let { check ->
                            {
                                androidx.compose.material3.Checkbox(checked = check(app), onCheckedChange = null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(app) },
                    )
                }
            }
        }
    }
}
