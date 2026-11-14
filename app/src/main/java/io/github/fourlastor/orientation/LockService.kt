package io.github.fourlastor.orientation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Holds the locked orientation against apps that ask for another one, like a fullscreen video
 * asking for landscape: the rotation settings alone only apply to apps that don't ask.
 *
 * The window manager ranks an orientation requested by an overlay window above the app's own, so
 * while the settings say "locked" this keeps an invisible 1px overlay asking for that orientation.
 * It follows the settings wherever they're changed from, and stops once auto-rotate is back on.
 */
class LockService : Service() {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = hold()
    }
    private var overlay: View? = null
    private var statusBarVisible = true
    private val flip = Runnable { flipUpsideDownAndBack() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Within 5 seconds of startForegroundService, whatever hold() decides next.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        // Only auto-rotate matters: changes to user_rotation reach the overlay through SCREEN_ORIENTATION_USER.
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hold()
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        overlay?.handler?.removeCallbacks(flip)
        overlay?.let { getSystemService(WindowManager::class.java).removeView(it) }
        overlay = null
        super.onDestroy()
    }

    private fun hold() {
        if (!shouldHold(this)) {
            stopSelf()
            return
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Fully transparent, so the system never counts it as obscuring touches meant for apps.
            alpha = 0f
            // "The user's rotation": with auto-rotate off the window manager resolves this to exactly
            // user_rotation, so it follows lock changes by itself. A fixed PORTRAIT/LANDSCAPE would let
            // each device pick which way up, which can differ from user_rotation.
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        }
        val windowManager = getSystemService(WindowManager::class.java)
        try {
            overlay?.let { windowManager.updateViewLayout(it, params) }
                ?: View(this).also {
                    it.setOnApplyWindowInsetsListener { _, insets ->
                        onStatusBarVisible(insets.isVisible(WindowInsets.Type.statusBars()))
                        insets
                    }
                    windowManager.addView(it, params)
                }.let { overlay = it }
        } catch (e: RuntimeException) {
            // The overlay permission was taken away while running: fall back to the settings alone.
            overlay = null
            stopSelf()
        }
    }

    /**
     * An app that asked for another orientation when going fullscreen can sit waiting for the
     * rotation that the lock now never gives it: YouTube in Firefox leaves the video at the top,
     * laid out for the page it came from, until the orientation changes. The overlay can't tell
     * which app asked, but it sees the status bar go when any app goes fullscreen.
     */
    private fun onStatusBarVisible(visible: Boolean) {
        val handler = overlay?.handler ?: return
        if (!visible && statusBarVisible) {
            // Only once the fullscreen settles, so a passing hide doesn't flip the screen.
            handler.postDelayed(flip, FULLSCREEN_SETTLE_MS)
        } else if (visible) {
            handler.removeCallbacks(flip)
        }
        statusBarVisible = visible
    }

    /**
     * Turns the screen upside down and back: an orientation change of the same size, which gets the
     * app to lay itself out again without ever leaving the locked orientation's shape.
     */
    private fun flipUpsideDownAndBack() {
        if (!shouldHold(this)) return
        val locked = Rotation.current(this)
        val upsideDown = (locked + 2) % 4
        Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, upsideDown)
        overlay?.postDelayed({
            // Unless the lock was changed meanwhile.
            if (Rotation.current(this) == upsideDown) {
                Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, locked)
            }
        }, FLIP_BACK_MS)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.lock_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rotation)
            .setContentTitle(getString(R.string.lock_notification_title))
            .setContentText(getString(R.string.lock_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lock"
        private const val NOTIFICATION_ID = 1
        // Only to skip a status bar that merely blinks: flipping right as fullscreen starts already
        // fixes the layout (tried on a HyperOS tablet), and this is time the video sits at the top.
        private const val FULLSCREEN_SETTLE_MS = 150L
        // Just enough for the window manager to see the first write on its own (writes ~60ms apart
        // were enough on the tablet). It doesn't shorten the flip: the second rotation waits for the
        // first one's animation anyway, about 0.6s.
        private const val FLIP_BACK_MS = 100L

        private fun shouldHold(context: Context) = !Rotation.isAuto(context) && Rotation.canOverride(context)

        /** Starts holding the lock when the settings say locked; otherwise the service stops itself. */
        fun sync(context: Context) {
            if (!shouldHold(context)) return
            try {
                context.startForegroundService(Intent(context, LockService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from the background right now: the settings lock still applies, and
                // the next start from the app or a tile picks it up.
            }
        }
    }
}
