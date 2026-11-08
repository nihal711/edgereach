package dev.nihal.thumbedge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nihal.thumbedge.data.PreviewBus
import dev.nihal.thumbedge.data.SettingsRepo
import dev.nihal.thumbedge.data.Side
import dev.nihal.thumbedge.ui.ExcludedAppsScreen
import dev.nihal.thumbedge.ui.FixScreen
import dev.nihal.thumbedge.ui.GestureSettingsScreen
import dev.nihal.thumbedge.ui.HandleScreen
import dev.nihal.thumbedge.ui.HomeScreen
import dev.nihal.thumbedge.ui.ThumbTheme
import dev.nihal.thumbedge.ui.VisibilityScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ThumbTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        dev.nihal.thumbedge.data.ServiceGuard.check(this, "app opened")
    }

    override fun onStop() {
        super.onStop()
        PreviewBus.highlight.value = null
        PreviewBus.zones.value = false
    }
}

enum class Route(val depth: Int) { HOME(0), LEFT(1), RIGHT(1), GESTURES(1), VISIBILITY(1), FIX(1), EXCLUDED(2) }

private val stackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { s -> s.map { it.name } },
    restore = { l -> l.map { Route.valueOf(it) }.toMutableStateList() },
)

@Composable
fun AppRoot() {
    val stack = rememberSaveable(saver = stackSaver) { mutableStateListOf(Route.HOME) }
    val go: (Route) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = back)
    val config by SettingsRepo.config.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            if (targetState.depth >= initialState.depth) {
                (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 8 } + fadeIn()) togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
            }
        },
        label = "nav",
    ) { route ->
        when (route) {
            Route.HOME -> HomeScreen(config, go)
            Route.LEFT -> HandleScreen(Side.LEFT, config, back)
            Route.RIGHT -> HandleScreen(Side.RIGHT, config, back)
            Route.GESTURES -> GestureSettingsScreen(config, back)
            Route.VISIBILITY -> VisibilityScreen(config, back, go)
            Route.EXCLUDED -> ExcludedAppsScreen(config, back)
            Route.FIX -> FixScreen(config, back)
        }
    }
}
