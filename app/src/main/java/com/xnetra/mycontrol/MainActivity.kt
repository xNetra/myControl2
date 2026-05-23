package com.xnetra.mycontrol

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.xnetra.mycontrol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — we just continue. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        // Request notification permission (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        bindUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindUi() {
        binding.tvOffTime.text = formatTime(prefs.offHour, prefs.offMinute)
        binding.tvOnTime.text = formatTime(prefs.onHour, prefs.onMinute)
        binding.switchWifi.isChecked = prefs.toggleWifi
        binding.switchData.isChecked = prefs.toggleData
        binding.switchAccessibility.isChecked = prefs.useAccessibility
        binding.switchEnable.isChecked = prefs.enabled

        binding.rowOffTime.setOnClickListener {
            pickTime(prefs.offHour, prefs.offMinute) { h, m ->
                prefs.offHour = h
                prefs.offMinute = m
                binding.tvOffTime.text = formatTime(h, m)
            }
        }
        binding.rowOnTime.setOnClickListener {
            pickTime(prefs.onHour, prefs.onMinute) { h, m ->
                prefs.onHour = h
                prefs.onMinute = m
                binding.tvOnTime.text = formatTime(h, m)
            }
        }
        binding.switchWifi.setOnCheckedChangeListener { _, v -> prefs.toggleWifi = v }
        binding.switchData.setOnCheckedChangeListener { _, v -> prefs.toggleData = v }
        binding.switchAccessibility.setOnCheckedChangeListener { _, v ->
            prefs.useAccessibility = v
            if (v && !ToggleAccessibilityService.isEnabled(this)) {
                openAccessibilitySettings()
            }
        }
        binding.switchEnable.setOnCheckedChangeListener { _, v ->
            prefs.enabled = v
            if (v) ensureExactAlarmPermission()
            ScheduleManager.rescheduleAll(this)
            refreshStatus()
        }
        binding.btnSave.setOnClickListener {
            if (prefs.enabled) {
                ScheduleManager.rescheduleAll(this)
                Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_enable_first, Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
        binding.btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnExactAlarm.setOnClickListener { ensureExactAlarmPermission(force = true) }
    }

    private fun refreshStatus() {
        binding.tvStatus.text = ScheduleManager.describeNext(this)
        binding.tvAccessibilityStatus.text =
            if (ToggleAccessibilityService.isEnabled(this))
                getString(R.string.accessibility_on)
            else
                getString(R.string.accessibility_off)
    }

    private fun pickTime(h: Int, m: Int, cb: (Int, Int) -> Unit) {
        TimePickerDialog(this, { _, hh, mm -> cb(hh, mm) }, h, m, true).show()
    }

    private fun formatTime(h: Int, m: Int): String =
        String.format("%02d:%02d", h, m)

    private fun openAccessibilitySettings() {
        Toast.makeText(this, R.string.toast_enable_accessibility, Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun ensureExactAlarmPermission(force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms() || force) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Throwable) { /* not all devices expose this */ }
            }
        }
    }
}
