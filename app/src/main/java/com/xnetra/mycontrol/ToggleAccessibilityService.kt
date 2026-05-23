package com.xnetra.mycontrol

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional auto-tap toggle service for Option A.
 *
 * The user must enable this service manually in Settings → Accessibility.
 *
 * When AlarmReceiver wants to perform a toggle, it broadcasts to this service.
 * The service then:
 *  1. Opens the Quick Settings panel via performGlobalAction.
 *  2. Looks for "Internet" / "Wi-Fi" / "Mobile data" tiles by text or contentDescription.
 *  3. Performs a click on the matching node.
 *  4. Closes the Quick Settings panel again.
 *
 * Layout / labels differ across phones. On OnePlus 13R (OxygenOS / Android 15)
 * the relevant tile labels are typically "Internet" (combined Wi-Fi+Data) or
 * separately "Wi-Fi" and "Mobile data". We try several common variants.
 */
class ToggleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TOGGLE) return
            val targets = intent.getStringArrayListExtra(EXTRA_TARGETS) ?: arrayListOf()
            val turnOn = intent.getBooleanExtra(EXTRA_TURN_ON, false)
            Log.i(TAG, "Toggle requested: $targets turnOn=$turnOn")
            performToggles(targets, turnOn)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected.")
        val filter = IntentFilter(ACTION_TOGGLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(toggleReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(toggleReceiver) } catch (_: Throwable) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { /* not used */ }

    /**
     * Open quick settings, find each target tile, click it.
     * "Clicking" a quick-settings tile toggles it. If the tile is already in the
     * desired state we accept the duplicate click — but in practice we only fire
     * at scheduled times so this is rare.
     */
    private fun performToggles(targets: List<String>, turnOn: Boolean) {
        if (targets.isEmpty()) return

        // Step 1: Open quick settings.
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

        // Step 2: After a short delay (panel must animate in), find and click tiles.
        handler.postDelayed({
            val root = rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "rootInActiveWindow null — cannot find tiles.")
                return@postDelayed
            }

            for (target in targets) {
                val labels = labelsFor(target)
                val node = findClickableByLabels(root, labels)
                if (node != null) {
                    Log.i(TAG, "Clicking tile for $target")
                    var clickable: AccessibilityNodeInfo? = node
                    while (clickable != null && !clickable.isClickable) {
                        clickable = clickable.parent
                    }
                    clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    Log.w(TAG, "Could not find tile for $target (tried: $labels)")
                }
            }

            // Step 3: Close quick settings.
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
            }, 600)
        }, 700)
    }

    private fun labelsFor(target: String): List<String> = when (target) {
        "wifi" -> listOf("Wi-Fi", "WiFi", "Wifi", "Wireless")
        "data" -> listOf("Mobile data", "Mobile Data", "Cellular data", "Data", "Internet")
        else -> emptyList()
    }

    /**
     * Walks the accessibility tree looking for a node whose text or
     * contentDescription matches any of the candidate labels (case-insensitive).
     */
    private fun findClickableByLabels(
        root: AccessibilityNodeInfo,
        labels: List<String>
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val text = node.text?.toString()?.trim()
            val cd = node.contentDescription?.toString()?.trim()
            for (label in labels) {
                if (matches(text, label) || matches(cd, label)) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                val c = node.getChild(i) ?: continue
                stack.addLast(c)
            }
        }
        return null
    }

    private fun matches(value: String?, label: String): Boolean {
        if (value.isNullOrBlank()) return false
        // Tolerant match: tile may say "Wi-Fi\nMyNetwork" or "Mobile data\nVodafone IN".
        return value.equals(label, ignoreCase = true) ||
            value.startsWith(label, ignoreCase = true) ||
            value.contains(label, ignoreCase = true)
    }

    companion object {
        private const val TAG = "MyControl"

        const val ACTION_TOGGLE = "com.xnetra.mycontrol.ACTION_ACCESSIBILITY_TOGGLE"
        const val EXTRA_TARGETS = "targets"
        const val EXTRA_TURN_ON = "turn_on"

        /** Check whether this service is currently enabled by the user. */
        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected =
                "${context.packageName}/${ToggleAccessibilityService::class.java.name}"
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val s = splitter.next()
                if (s.equals(expected, ignoreCase = true)) return true
            }
            return false
        }

        fun requestToggle(context: Context, targets: List<String>, turnOn: Boolean) {
            val intent = Intent(ACTION_TOGGLE).apply {
                setPackage(context.packageName)
                putStringArrayListExtra(EXTRA_TARGETS, ArrayList(targets))
                putExtra(EXTRA_TURN_ON, turnOn)
            }
            context.sendBroadcast(intent)
        }
    }
}
