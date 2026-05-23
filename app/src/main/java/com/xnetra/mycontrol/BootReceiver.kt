package com.xnetra.mycontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-installs scheduled alarms after the device boots, since AlarmManager
 * loses all pending alarms on shutdown.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("MyControl", "Boot received: ${intent.action}")
        ScheduleManager.rescheduleAll(context)
    }
}
