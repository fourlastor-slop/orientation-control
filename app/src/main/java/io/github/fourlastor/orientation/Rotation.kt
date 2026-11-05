package io.github.fourlastor.orientation

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.provider.Settings
import android.view.Display

/** Mirrors `adb shell settings put system user_rotation|accelerometer_rotation ...`. */
object Rotation {
    /**
     * user_rotation is relative to the panel's natural orientation: on a portrait-first device
     * 0/1/2 are portrait/landscape/reverse portrait, on a landscape-first tablet it's 1/0/3.
     */
    enum class Orientation(private val naturalPortrait: Int, private val naturalLandscape: Int) {
        PORTRAIT(0, 1),
        LANDSCAPE(1, 0),
        REVERSE_PORTRAIT(2, 3);

        fun value(context: Context) =
            if (isNaturallyLandscape(context)) naturalLandscape else naturalPortrait

        companion object {
            fun of(context: Context, value: Int) = entries.firstOrNull { it.value(context) == value }
        }
    }

    private fun isNaturallyLandscape(context: Context): Boolean {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        // Mode dimensions are reported in the natural orientation, regardless of current rotation.
        return display.mode.physicalWidth > display.mode.physicalHeight
    }

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isAuto(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun setAuto(context: Context, enabled: Boolean) {
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 1 else 0)
    }

    fun current(context: Context): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, 0)

    /**
     * user_rotation is only honoured while accelerometer rotation is off. Turning it off can make
     * the system freeze the current rotation into user_rotation, so write ours on both sides.
     */
    fun lock(context: Context, orientation: Orientation) {
        val value = orientation.value(context)
        Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, value)
        setAuto(context, false)
        Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, value)
    }
}
