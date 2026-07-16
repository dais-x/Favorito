package com.favorito

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FavoritoTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        SettingsStore.init(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        SettingsStore.init(this)
        SettingsStore.setMasterEnabled(!SettingsStore.masterEnabled)
        if (!SettingsStore.masterEnabled) {
            PlaybackStateHolder.clear()
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (SettingsStore.masterEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Favorito"
            subtitle = if (SettingsStore.masterEnabled) "Listening for track endings" else "Off"
            updateTile()
        }
    }
}
