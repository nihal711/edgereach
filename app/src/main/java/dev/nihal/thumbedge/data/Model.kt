package dev.nihal.thumbedge.data

import kotlinx.serialization.Serializable

enum class Side(val title: String) {
    LEFT("Left handle"),
    RIGHT("Right handle");

    val other: Side get() = if (this == LEFT) RIGHT else LEFT
}

enum class Direction(val title: String) {
    STRAIGHT("Swipe straight"),
    UP("Swipe diagonally up"),
    DOWN("Swipe diagonally down"),
}

enum class Length { SHORT, LONG }

@Serializable
enum class LongMode { DISTANCE, HOLD }

fun gestureKey(d: Direction, l: Length) = "${d.name}_${l.name}"

fun lengthTitle(l: Length, mode: LongMode) = when {
    l == Length.SHORT -> "Short swipe"
    mode == LongMode.DISTANCE -> "Long swipe"
    else -> "Swipe and hold"
}

val DEFAULT_ACTIONS: Map<String, String> = mapOf(
    gestureKey(Direction.STRAIGHT, Length.SHORT) to ActionType.BACK.name,
    gestureKey(Direction.STRAIGHT, Length.LONG) to ActionType.PREVIOUS_APP.name,
    gestureKey(Direction.UP, Length.SHORT) to ActionType.HOME.name,
    gestureKey(Direction.UP, Length.LONG) to ActionType.RECENTS.name,
    gestureKey(Direction.DOWN, Length.SHORT) to ActionType.NOTIFICATIONS.name,
    gestureKey(Direction.DOWN, Length.LONG) to ActionType.QUICK_SETTINGS.name,
)

/** Colour choices for a handle. The first entry (null) means "follow the system accent". */
val HANDLE_COLORS: List<Int?> = listOf(
    null,
    0xFFFFFFFF.toInt(),
    0xFF9E9E9E.toInt(),
    0xFF000000.toInt(),
    0xFFEF5350.toInt(),
    0xFFFFA726.toInt(),
    0xFFFFEE58.toInt(),
    0xFF66BB6A.toInt(),
    0xFF26A69A.toInt(),
    0xFF42A5F5.toInt(),
    0xFFAB47BC.toInt(),
    0xFFEC407A.toInt(),
)

@Serializable
data class HandleConfig(
    val enabled: Boolean = true,
    /** Centre of the handle, as a fraction of screen height from the top. */
    val position: Float = 0.62f,
    /** Height as a fraction of screen height. */
    val height: Float = 0.2f,
    val widthDp: Int = 16,
    /** ARGB colour, or null to follow the system accent colour. */
    val color: Int? = null,
    val opacity: Float = 0.3f,
    val actions: Map<String, String> = DEFAULT_ACTIONS,
) {
    fun action(d: Direction, l: Length): String = actions[gestureKey(d, l)] ?: ActionType.NONE.name
    fun withAction(d: Direction, l: Length, a: String) = copy(actions = actions + (gestureKey(d, l) to a))
    fun assignedCount() = Direction.entries.sumOf { d ->
        Length.entries.count { l -> action(d, l) != ActionType.NONE.name }
    }
}

@Serializable
data class Config(
    val enabled: Boolean = true,
    val left: HandleConfig = HandleConfig(),
    val right: HandleConfig = HandleConfig(),
    val longMode: LongMode = LongMode.DISTANCE,
    val shortDistDp: Int = 36,
    val longDistDp: Int = 140,
    val holdMs: Int = 400,
    /** Swipes within ±this many degrees of horizontal count as "straight". */
    val straightAngle: Int = 28,
    val vibrate: Boolean = true,
    val showIndicator: Boolean = true,
    val passThrough: Boolean = true,
    val hideLandscape: Boolean = false,
    val hideKeyboard: Boolean = true,
    val showOnLockscreen: Boolean = false,
    val excluded: Set<String> = emptySet(),
) {
    fun handle(s: Side) = if (s == Side.LEFT) left else right
    fun withHandle(s: Side, h: HandleConfig) = if (s == Side.LEFT) copy(left = h) else copy(right = h)
}
