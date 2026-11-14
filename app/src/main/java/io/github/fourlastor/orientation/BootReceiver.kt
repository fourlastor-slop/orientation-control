package io.github.fourlastor.orientation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The settings survive a reboot but the overlay doesn't: start holding the lock again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) LockService.sync(context)
    }
}
