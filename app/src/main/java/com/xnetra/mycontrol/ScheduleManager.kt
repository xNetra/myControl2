package com.xnetra.mycontrol

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Schedules daily ON/OFF alarms using AlarmManager.
 * Uses setExactAndAllowWhileIdle so the alarm fires even in Doze mode.
 * After each fire, AlarmReceiver re-schedules for the next day.
 */
object ScheduleManager {

    private const val TAG = "MyControl"

    const val ACTION_TURN_OFF = "com.xnetra.mycontrol.ACTION_TURN_OFF"
    const val ACTION_TURN_ON = "com.xnetra.mycontrol.ACTION_TURN_ON"

    private const val REQ_OFF = 1001
    private const val REQ_ON = 1002

    /**
     * Cancel any pending alarms and (if enabled) schedule the next daily ON and OFF.
     */
    fun rescheduleAll(context: Context) {
        val prefs = Prefs(context)
        cancel(context, ACTION_TURN_OFF, REQ_OFF)
        cancel(context, ACTION_TURN_ON, REQ_ON)
        if (!prefs.enabled) {
            Log.i(TAG, "Schedule disabled — not setting alarms.")
            return
        }
        scheduleNext(context, ACTION_TURN_OFF, REQ_OFF, prefs.offHour, prefs.offMinute)
        scheduleNext(context, ACTION_TURN_ON, REQ_ON, prefs.onHour, prefs.onMinute)
    }

    /**
     * Schedule a single alarm for the next occurrence of the given hour:minute.
     * Call this both initially and after the alarm fires (to set tomorrow's).
     */
    fun scheduleNext(
        context: Context,
        action: String,
        requestCode: Int,
        hour: Int,
        minute: Int
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrenceMillis(hour, minute)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // canScheduleExactAlarms may return false on Android 12+.
                // We declared USE_EXACT_ALARM (S_V2+) which is auto-granted; on 12 only
                // SCHEDULE_EXACT_ALARM is granted by default. Fall back to inexact if needed.
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            Log.i(TAG, "Alarm scheduled: $action at ${fmt.format(Date(triggerAt))}")
        } catch (se: SecurityException) {
            Log.e(TAG, "Failed to set alarm (permission denied): ${se.message}")
        }
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply { this.action = action }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pending)
    }

    /**
     * Compute the next time (in millis) that hour:minute will occur.
     * If today's time is in the future, returns today's; otherwise tomorrow's.
     */
    fun nextOccurrenceMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /** For UI display */
    fun describeNext(context: Context): String {
        val prefs = Prefs(context)
        if (!prefs.enabled) return "Disabled"
        val nextOff = nextOccurrenceMillis(prefs.offHour, prefs.offMinute)
        val nextOn = nextOccurrenceMillis(prefs.onHour, prefs.onMinute)
        val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        return if (nextOff < nextOn) {
            "Next: OFF at ${fmt.format(Date(nextOff))}"
        } else {
            "Next: ON at ${fmt.format(Date(nextOn))}"
        }
    }
}
