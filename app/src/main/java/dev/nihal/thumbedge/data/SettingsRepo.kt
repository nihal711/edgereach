package dev.nihal.thumbedge.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** Single source of truth for settings. The app UI and the service run in one process and share it. */
object SettingsRepo {
    private const val KEY = "config_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(Config())
    val config: StateFlow<Config> = state.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("thumbedge", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(Config.serializer(), it) }.getOrNull() }
            ?.let { state.value = it }
    }

    fun update(transform: (Config) -> Config) {
        state.update(transform)
        prefs?.edit()?.putString(KEY, json.encodeToString(Config.serializer(), state.value))?.apply()
    }
}

/** Lets the settings screen ask the service to highlight a handle while its sliders move. */
object PreviewBus {
    val highlight = MutableStateFlow<Side?>(null)
}
