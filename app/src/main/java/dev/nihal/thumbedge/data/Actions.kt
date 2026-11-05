package dev.nihal.thumbedge.data

import android.content.ComponentName
import android.content.Context

enum class Category(val title: String) {
    NAVIGATION("Navigation"),
    PANELS("Panels & screen"),
    MEDIA("Media & sound"),
    TOOLS("Tools"),
    APPS("Apps"),
}

enum class ActionType(val label: String, val category: Category, val hint: String? = null) {
    NONE("No action", Category.NAVIGATION),
    BACK("Back", Category.NAVIGATION),
    HOME("Home", Category.NAVIGATION),
    RECENTS("Recent apps", Category.NAVIGATION),
    PREVIOUS_APP("Previous app", Category.NAVIGATION, "Jump back to the last app you used"),
    APP_DRAWER("App drawer", Category.NAVIGATION, "Opens the launcher's app list"),
    SPLIT_SCREEN("Split screen", Category.NAVIGATION, "Not every app supports it"),

    NOTIFICATIONS("Notification panel", Category.PANELS),
    QUICK_SETTINGS("Quick settings", Category.PANELS),
    CLOSE_SHADE("Close panels", Category.PANELS, "Closes notifications and quick settings"),
    SCREENSHOT("Screenshot", Category.PANELS),
    LOCK_SCREEN("Turn off screen", Category.PANELS),
    POWER_MENU("Power menu", Category.PANELS),

    MEDIA_PLAY_PAUSE("Play / pause", Category.MEDIA),
    MEDIA_NEXT("Next track", Category.MEDIA),
    MEDIA_PREVIOUS("Previous track", Category.MEDIA),
    VOLUME_PANEL("Volume panel", Category.MEDIA),
    VOLUME_UP("Volume up", Category.MEDIA),
    VOLUME_DOWN("Volume down", Category.MEDIA),
    RINGER("Sound / vibrate", Category.MEDIA, "Switches the ringer between sound and vibrate"),

    FLASHLIGHT("Flashlight", Category.TOOLS),
    ROTATION("Auto-rotate", Category.TOOLS, "Needs \"Modify system settings\" the first time"),
    ASSISTANT("Assistant", Category.TOOLS, "Gemini or your default assistant"),

    LAUNCH_APP("Open app…", Category.APPS, "Pick any installed app"),
}

/** An action as stored in settings: "BACK", or "LAUNCH_APP:com.pkg/com.pkg.Activity". */
data class Action(val type: ActionType, val component: ComponentName? = null) {
    fun encode(): String =
        if (type == ActionType.LAUNCH_APP && component != null) "$APP_PREFIX${component.flattenToString()}"
        else type.name

    companion object {
        private const val APP_PREFIX = "LAUNCH_APP:"

        fun parse(s: String?): Action {
            if (s == null) return Action(ActionType.NONE)
            if (s.startsWith(APP_PREFIX)) {
                val cmp = ComponentName.unflattenFromString(s.removePrefix(APP_PREFIX))
                return if (cmp != null) Action(ActionType.LAUNCH_APP, cmp) else Action(ActionType.NONE)
            }
            return Action(ActionType.entries.firstOrNull { it.name == s } ?: ActionType.NONE)
        }
    }
}

fun actionLabel(ctx: Context, encoded: String): String {
    val a = Action.parse(encoded)
    if (a.type != ActionType.LAUNCH_APP) return a.type.label
    val cmp = a.component ?: return "Open app"
    val pm = ctx.packageManager
    return runCatching { pm.getActivityInfo(cmp, 0).loadLabel(pm).toString() }
        .recoverCatching { pm.getApplicationInfo(cmp.packageName, 0).loadLabel(pm).toString() }
        .getOrDefault("App not installed")
}
