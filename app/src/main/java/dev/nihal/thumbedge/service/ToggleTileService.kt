package dev.nihal.thumbedge.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.nihal.thumbedge.data.SettingsRepo

/** Quick Settings tile that pauses or resumes the edge handles. */
class ToggleTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        // Opening Quick Settings is a handy moment to turn the service back on if it was switched off.
        dev.nihal.thumbedge.data.ServiceGuard.check(this, "quick settings opened")
        refresh()
    }

    override fun onClick() {
        super.onClick()
        SettingsRepo.init(this)
        SettingsRepo.update { it.copy(enabled = !it.enabled) }
        refresh()
    }

    private fun refresh() {
        SettingsRepo.init(this)
        val tile = qsTile ?: return
        val on = SettingsRepo.config.value.enabled
        val serviceOn = EdgeService.running.value
        tile.state = if (on && serviceOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !serviceOn -> "Service off"
            on -> "On"
            else -> "Paused"
        }
        tile.updateTile()
    }
}
