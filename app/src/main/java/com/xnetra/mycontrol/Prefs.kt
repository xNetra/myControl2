package com.xnetra.mycontrol

import android.content.Context
import android.content.SharedPreferences

/**
 * Wrapper around SharedPreferences for storing schedule settings.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var offHour: Int
        get() = sp.getInt(KEY_OFF_HOUR, 23)
        set(v) = sp.edit().putInt(KEY_OFF_HOUR, v).apply()

    var offMinute: Int
        get() = sp.getInt(KEY_OFF_MINUTE, 0)
        set(v) = sp.edit().putInt(KEY_OFF_MINUTE, v).apply()

    var onHour: Int
        get() = sp.getInt(KEY_ON_HOUR, 7)
        set(v) = sp.edit().putInt(KEY_ON_HOUR, v).apply()

    var onMinute: Int
        get() = sp.getInt(KEY_ON_MINUTE, 0)
        set(v) = sp.edit().putInt(KEY_ON_MINUTE, v).apply()

    var toggleWifi: Boolean
        get() = sp.getBoolean(KEY_WIFI, true)
        set(v) = sp.edit().putBoolean(KEY_WIFI, v).apply()

    var toggleData: Boolean
        get() = sp.getBoolean(KEY_DATA, true)
        set(v) = sp.edit().putBoolean(KEY_DATA, v).apply()

    /** If true, app tries to use Accessibility service to auto-tap toggles.
     *  If false, it just opens settings panel and waits for user. */
    var useAccessibility: Boolean
        get() = sp.getBoolean(KEY_USE_ACCESSIBILITY, false)
        set(v) = sp.edit().putBoolean(KEY_USE_ACCESSIBILITY, v).apply()

    /** Master enable switch. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    companion object {
        private const val NAME = "mycontrol_prefs"
        private const val KEY_OFF_HOUR = "off_hour"
        private const val KEY_OFF_MINUTE = "off_minute"
        private const val KEY_ON_HOUR = "on_hour"
        private const val KEY_ON_MINUTE = "on_minute"
        private const val KEY_WIFI = "toggle_wifi"
        private const val KEY_DATA = "toggle_data"
        private const val KEY_USE_ACCESSIBILITY = "use_accessibility"
        private const val KEY_ENABLED = "enabled"
    }
}
