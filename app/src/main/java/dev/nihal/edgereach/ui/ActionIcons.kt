package dev.nihal.edgereach.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.West
import androidx.compose.ui.graphics.vector.ImageVector
import dev.nihal.edgereach.data.ActionType
import dev.nihal.edgereach.data.Direction
import dev.nihal.edgereach.data.Side

fun ActionType.icon(): ImageVector = when (this) {
    ActionType.NONE -> Icons.Filled.Block
    ActionType.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    ActionType.HOME -> Icons.Filled.Home
    ActionType.RECENTS -> Icons.Filled.Layers
    ActionType.PREVIOUS_APP -> Icons.Filled.SwapHoriz
    ActionType.APP_DRAWER -> Icons.Filled.Apps
    ActionType.SPLIT_SCREEN -> Icons.Filled.VerticalSplit
    ActionType.NOTIFICATIONS -> Icons.Filled.Notifications
    ActionType.QUICK_SETTINGS -> Icons.Filled.Tune
    ActionType.CLOSE_SHADE -> Icons.Filled.ExpandLess
    ActionType.SCREENSHOT -> Icons.Filled.Screenshot
    ActionType.LOCK_SCREEN -> Icons.Filled.Lock
    ActionType.POWER_MENU -> Icons.Filled.PowerSettingsNew
    ActionType.MEDIA_PLAY_PAUSE -> Icons.Filled.PlayArrow
    ActionType.MEDIA_NEXT -> Icons.Filled.SkipNext
    ActionType.MEDIA_PREVIOUS -> Icons.Filled.SkipPrevious
    ActionType.VOLUME_PANEL -> Icons.Filled.Speaker
    ActionType.VOLUME_UP -> Icons.AutoMirrored.Filled.VolumeUp
    ActionType.VOLUME_DOWN -> Icons.AutoMirrored.Filled.VolumeDown
    ActionType.RINGER -> Icons.Filled.Vibration
    ActionType.FLASHLIGHT -> Icons.Filled.FlashlightOn
    ActionType.ROTATION -> Icons.Filled.ScreenRotation
    ActionType.ASSISTANT -> Icons.Filled.Mic
    ActionType.LAUNCH_APP -> Icons.Filled.Apps
}

fun directionIcon(side: Side, d: Direction): ImageVector = when (d) {
    Direction.STRAIGHT -> if (side == Side.RIGHT) Icons.Filled.West else Icons.Filled.East
    Direction.UP -> if (side == Side.RIGHT) Icons.Filled.NorthWest else Icons.Filled.NorthEast
    Direction.DOWN -> if (side == Side.RIGHT) Icons.Filled.SouthWest else Icons.Filled.SouthEast
}
