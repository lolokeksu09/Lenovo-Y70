package com.ramwiper.next

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ramwiper.next.domain.AutomationPolicy

class MemoryTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val percent = runCatching { AutomationPolicy.availablePercent(readBasicMemory(this)) }.getOrNull()
        qsTile?.apply {
            label = "RAM Wiper"
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = percent?.let { "$it% доступно" } ?: "Открыть"
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 703, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else startActivityAndCollapse(intent)
    }
}
