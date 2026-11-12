package dev.nihal.edgereach.data

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dev.nihal.edgereach.service.EdgeService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small on-device log so we can see why the service stopped. Shown on the "Fix problems" screen. */
object DiagLog {
    private var file: File? = null
    private val fmt = SimpleDateFormat("MMM d HH:mm:ss", Locale.US)

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.filesDir, "diag.log")
    }

    @Synchronized
    fun add(msg: String) {
        android.util.Log.i("EdgeReach", msg)
        val f = file ?: return
        runCatching {
            f.appendText("${fmt.format(Date())}  $msg\n")
            if (f.length() > 64_000) f.writeText(f.readLines().takeLast(150).joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun lines(): List<String> = runCatching {
        file?.takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() }?.takeLast(200)?.reversed()
    }.getOrNull() ?: emptyList()

    @Synchronized
    fun clear() {
        runCatching { file?.writeText("") }
    }

    /** Logs Android's own record of why our previous processes ended (force stop, low memory, crash…). */
    fun recordExitReasons(ctx: Context) {
        val prefs = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_exit_ts", 0L)
        val am = ctx.getSystemService(ActivityManager::class.java)
        val exits = runCatching { am.getHistoricalProcessExitReasons(ctx.packageName, 0, 8) }.getOrNull() ?: return
        val fresh = exits.filter { it.timestamp > last }.sortedBy { it.timestamp }
        for (e in fresh) {
            val desc = e.description?.let { " – $it" } ?: ""
            add("Previous run ended at ${fmt.format(Date(e.timestamp))}: ${reasonName(e.reason)}$desc")
        }
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("last_exit_ts", maxOf(it, last)).apply() }
    }

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> "force-stopped (by the user or a system cleaner)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by the user"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
        ApplicationExitInfo.REASON_CRASH -> "crashed"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crashed (native)"
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for battery/resource use"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
        ApplicationExitInfo.REASON_FREEZER -> "frozen by the system"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "app updated"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited"
        ApplicationExitInfo.REASON_OTHER -> "killed by the system (other)"
        else -> "reason $r"
    }
}

/**
 * Turns the accessibility service back on when the phone switches it off. Needs the
 * WRITE_SECURE_SETTINGS permission, which can only be granted once over adb.
 */
object ServiceGuard {
    const val ADB_GRANT = "adb shell pm grant dev.nihal.edgereach android.permission.WRITE_SECURE_SETTINGS"

    fun component(ctx: Context) = ComponentName(ctx, EdgeService::class.java)

    fun hasSecurePermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enabledInSettings(ctx: Context): Boolean {
        val me = component(ctx)
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    /** Re-enables the service if allowed. Returns true if it changed anything. */
    fun check(ctx: Context, reason: String): Boolean {
        SettingsRepo.init(ctx)
        DiagLog.init(ctx)
        val cfg = SettingsRepo.config.value
        if (!EdgeService.running.value) BackGestureControl.sync(ctx, cfg, serviceAlive = false)
        if (!cfg.autoRepair || !hasSecurePermission(ctx) || enabledInSettings(ctx)) return false
        return runCatching {
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val entries = current.split(':').map { it.trim() }
                .filter { ComponentName.unflattenFromString(it) != null } + component(ctx).flattenToString()
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, entries.distinct().joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            DiagLog.add("Service was off – turned it back on ($reason)")
            true
        }.getOrElse {
            DiagLog.add("Couldn't turn the service back on: ${it.message}")
            false
        }
    }
}

/**
 * Optionally turns off the system back swipe on edges that have a handle, by setting Android's
 * back-gesture width for that edge to zero. The original values are saved and put back when
 * the service stops or the option is turned off. Needs WRITE_SECURE_SETTINGS.
 */
object BackGestureControl {
    private const val LEFT = "back_gesture_inset_scale_left"
    private const val RIGHT = "back_gesture_inset_scale_right"
    private const val UNSET = "__unset__"

    fun isBlocked(ctx: Context, side: Side): Boolean =
        Settings.Secure.getString(ctx.contentResolver, key(side))?.toFloatOrNull() == 0f

    @Synchronized
    fun sync(ctx: Context, cfg: Config, serviceAlive: Boolean) {
        if (!ServiceGuard.hasSecurePermission(ctx)) return
        val saved = ctx.getSharedPreferences("backgesture", Context.MODE_PRIVATE)
        val cr = ctx.contentResolver
        for (side in Side.entries) {
            val k = key(side)
            val want = serviceAlive && cfg.enabled && cfg.blockSystemBack && cfg.handle(side).enabled
            runCatching {
                if (want) {
                    val cur = Settings.Secure.getString(cr, k)
                    if (cur?.toFloatOrNull() != 0f) {
                        if (!saved.contains(k)) saved.edit().putString(k, cur ?: UNSET).commit()
                        Settings.Secure.putString(cr, k, "0")
                        DiagLog.add("System back swipe off on the ${side.name.lowercase()} edge")
                    }
                } else if (saved.contains(k)) {
                    val orig = saved.getString(k, UNSET)
                    Settings.Secure.putString(cr, k, if (orig == UNSET) "1.0" else orig)
                    saved.edit().remove(k).commit()
                    DiagLog.add("System back swipe restored on the ${side.name.lowercase()} edge")
                }
            }.onFailure { DiagLog.add("Back swipe setting failed: ${it.message}") }
        }
    }

    private fun key(side: Side) = if (side == Side.LEFT) LEFT else RIGHT
}
