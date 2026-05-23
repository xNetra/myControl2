package com.xnetra.mycontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Fired by AlarmManager at the scheduled time.
 *
 *  - Always re-schedules the same alarm for the next day.
 *  - If accessibility mode is OFF (default), shows a notification AND opens the
 *    Wi-Fi or Mobile Data settings panel so the user can tap the toggle.
 *  - If accessibility mode is ON, signals ToggleAccessibilityService to perform
 *    the toggle automatically.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Alarm fired: $action")

        val prefs = Prefs(context)
        val turnOn = action == ScheduleManager.ACTION_TURN_ON

        // 1) Re-arm tomorrow's alarm immediately.
        if (turnOn) {
            ScheduleManager.scheduleNext(
                context,
                ScheduleManager.ACTION_TURN_ON,
                REQ_ON,
                prefs.onHour,
                prefs.onMinute
            )
        } else {
            ScheduleManager.scheduleNext(
                context,
                ScheduleManager.ACTION_TURN_OFF,
                REQ_OFF,
                prefs.offHour,
                prefs.offMinute
            )
        }

        // 2) Decide what to do.
        if (prefs.useAccessibility && ToggleAccessibilityService.isEnabled(context)) {
            // Option A: tell the accessibility service to toggle.
            val targets = mutableListOf<String>().apply {
                if (prefs.toggleWifi) add("wifi")
                if (prefs.toggleData) add("data")
            }
            ToggleAccessibilityService.requestToggle(context, targets, turnOn)
        } else {
            // Option B: notification + open the relevant settings panel.
            showReminderNotification(context, turnOn, prefs)
            openSettingsPanel(context, prefs)
        }
    }

    private fun showReminderNotification(context: Context, turnOn: Boolean, prefs: Prefs) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Schedule reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val whatList = buildList {
            if (prefs.toggleWifi) add("Wi-Fi")
            if (prefs.toggleData) add("Mobile Data")
        }
        val what = if (whatList.isEmpty()) "connections" else whatList.joinToString(" & ")
        val verb = if (turnOn) "ON" else "OFF"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("My Control — turn $verb")
            .setContentText("Tap the $what toggle to turn it $verb.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        nm.notify(if (turnOn) NOTIF_ON else NOTIF_OFF, notif)
    }

    /**
     * Open Android's built-in Wi-Fi (and/or Mobile Data) settings panel.
     * On Android 10+ the lightweight in-line panel is shown; otherwise full settings.
     * User just taps the toggle.
     */
    private fun openSettingsPanel(context: Context, prefs: Prefs) {
        // Settings.Panel.* is API 29+. On older devices fall back to wireless settings.
        val action: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when {
                    prefs.toggleWifi && prefs.toggleData -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    prefs.toggleWifi -> Settings.Panel.ACTION_WIFI
                    prefs.toggleData -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                    else -> return
                }
            } else {
                Settings.ACTION_WIRELESS_SETTINGS
            }
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not open settings panel: ${t.message}")
            val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { context.startActivity(fallback) } catch (_: Throwable) {}
        }
    }

    companion object {
        private const val TAG = "MyControl"
        private const val CHANNEL_ID = "schedule_reminders"
        private const val NOTIF_OFF = 1
        private const val NOTIF_ON = 2
        private const val REQ_OFF = 1001
        private const val REQ_ON = 1002
    }
}
