package io.github.fourlastor.orientation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class RotationTileService : TileService() {
    abstract fun isActive(): Boolean
    abstract fun apply()

    // Keeps the tile in sync with changes made elsewhere (other tiles, the app, adb).
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    override fun onStartListening() {
        listOf(Settings.System.ACCELEROMETER_ROTATION, Settings.System.USER_ROTATION).forEach {
            contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, observer)
        }
        refresh()
    }

    override fun onStopListening() = contentResolver.unregisterContentObserver(observer)

    override fun onClick() {
        if (!Rotation.canWrite(this)) {
            openPermissionScreen()
            return
        }
        apply()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (Rotation.canWrite(this) && isActive()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPermissionScreen() {
        val intent = Rotation.permissionIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

abstract class LockTileService(private val orientation: Rotation.Orientation) : RotationTileService() {
    override fun isActive() = !Rotation.isAuto(this) && Rotation.current(this) == orientation.value(this)
    override fun apply() = Rotation.lock(this, orientation)
}

class PortraitTile : LockTileService(Rotation.Orientation.PORTRAIT)
class ReversePortraitTile : LockTileService(Rotation.Orientation.REVERSE_PORTRAIT)
class LandscapeTile : LockTileService(Rotation.Orientation.LANDSCAPE)

class AutoRotateTile : RotationTileService() {
    override fun isActive() = Rotation.isAuto(this)
    override fun apply() = Rotation.setAuto(this, !Rotation.isAuto(this))
}
