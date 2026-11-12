package dev.nihal.edgereach.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Single source of truth for settings. The app UI and the service run in one process and share it.
 *
 * Settings are stored as JSON in SharedPreferences, which Android keeps across app updates. To make
 * sure an update never loses them:
 * - unknown fields are ignored and missing fields get their defaults,
 * - a bad value in one field falls back to that field's default instead of failing the whole load,
 * - older formats are migrated in [migrate],
 * - the raw settings are copied aside before the first run of a new version, and kept if they
 *   can't be read, so they are never silently overwritten.
 */
object SettingsRepo {
    private const val KEY = "config_v1"
    private const val KEY_BEFORE_UPDATE = "config_before_update"
    private const val KEY_LAST_VERSION = "last_version_code"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(Config())
    val config: StateFlow<Config> = state.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY, null)

        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        val lastVersion = p.getLong(KEY_LAST_VERSION, 0L)
        if (version != lastVersion) {
            val e = p.edit().putLong(KEY_LAST_VERSION, version)
            if (raw != null) e.putString(KEY_BEFORE_UPDATE, raw)
            e.commit()
        }

        if (raw == null) return
        val decoded = decode(raw)
        if (decoded != null) {
            state.value = decoded
        } else {
            p.edit().putString("config_unreadable_${System.currentTimeMillis()}", raw).commit()
            DiagLog.init(ctx)
            DiagLog.add("Couldn't read the saved settings; kept a copy and started from defaults")
        }
    }

    /** Parses saved settings from any app version, or null if they can't be read at all. */
    fun decode(raw: String): Config? = runCatching {
        migrate(raw, json.decodeFromString(Config.serializer(), raw))
    }.getOrNull()

    /** Upgrades settings saved by older versions. */
    private fun migrate(raw: String, c: Config): Config {
        val fields = runCatching { json.parseToJsonElement(raw).jsonObject.keys }.getOrDefault(emptySet())
        var m = c
        // Up to 1.6 one "straight zone" angle applied to both diagonals.
        if ("upAngle" !in fields) m = m.copy(upAngle = c.straightAngle)
        if ("downAngle" !in fields) m = m.copy(downAngle = c.straightAngle)
        return m
    }

    fun encode(c: Config): String = json.encodeToString(Config.serializer(), c)

    fun update(transform: (Config) -> Config) {
        state.update(transform)
        prefs?.edit()?.putString(KEY, encode(state.value))?.apply()
    }
}

/** Lets the settings screens ask the service for temporary on-screen previews. */
object PreviewBus {
    /** Highlight a handle while its size or position sliders move. */
    val highlight = MutableStateFlow<Side?>(null)

    /** Show the swipe-zone fans on the handles while sensitivity sliders move. */
    val zones = MutableStateFlow(false)
}
