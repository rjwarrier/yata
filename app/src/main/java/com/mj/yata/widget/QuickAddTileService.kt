package com.mj.yata.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that opens [QuickAddDialogActivity] with no preset target — the same
 * lightweight capture overlay the Quick Add widget uses, reachable from the notification shade
 * without a home-screen widget placed. Stateless: the tile is a launcher, never toggles.
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // A launch-only tile should never look "off"/dimmed, which is the default state.
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickAddDialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The Intent overload throws UnsupportedOperationException on API 34+.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
