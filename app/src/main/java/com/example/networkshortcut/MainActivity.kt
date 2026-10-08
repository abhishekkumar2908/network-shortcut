package com.example.networkshortcut

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Popup that shows the mobile data SIM and the network type you are on now,
 * then switches to the other one (4G or 5G) when you tap it.
 *
 * Android does not let normal apps change the network type, so the switch is
 * done by NetworkAccessibilityService tapping through the Settings screen.
 */
class MainActivity : Activity() {

    private var subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)

        // Needed only to read the current network type.
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        // Which SIM carries mobile data right now?
        subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val valid = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val base = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val tm = if (valid) base.createForSubscriptionId(subId) else base
        val carrier = tm.simOperatorName.ifBlank { tm.networkOperatorName }
        val current = currentType(tm)

        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = if (carrier.isBlank()) "Mobile data SIM not detected" else "Mobile data SIM: $carrier"
            textSize = 18f
        })

        root.addView(TextView(this).apply {
            text = "Currently on: ${current ?: "unknown"}"
            setPadding(0, pad / 4, 0, pad / 2)
        })

        if (serviceEnabled()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                choiceButton("4G", current == "4G"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(
                choiceButton("5G", current == "5G"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            root.addView(row)
        } else {
            root.addView(TextView(this).apply {
                text = "One-time setup: turn on \"Network Shortcut\" under Accessibility " +
                    "(Downloaded apps or Installed apps) so it can switch for you.\n\n" +
                    "If it is greyed out: long-press the app icon, tap App info, then the " +
                    "3-dot menu, then Allow restricted settings."
                setPadding(0, 0, 0, pad / 2)
            })
            root.addView(Button(this).apply {
                text = "Open Accessibility settings"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            })
        }

        setContentView(root)
    }

    /** "5G" or "4G" for the SIM's current mobile data connection, else null. */
    private fun currentType(tm: TelephonyManager): String? {
        return try {
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                else -> null
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }

    private fun choiceButton(label: String, isCurrent: Boolean) = Button(this).apply {
        text = if (isCurrent) "$label (current)" else label
        isEnabled = !isCurrent
        setOnClickListener { switchTo(label) }
    }

    private fun switchTo(label: String) {
        NetworkAccessibilityService.request(label)
        if (openNetworkSettings()) {
            Toast.makeText(applicationContext, "Switching to $label...", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            NetworkAccessibilityService.cancel()
            Toast.makeText(this, "Network settings not available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens this SIM's mobile network settings, trying the closest screen first. */
    private fun openNetworkSettings(): Boolean {
        val screens = listOf(
            Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            Settings.ACTION_DATA_ROAMING_SETTINGS,
            Settings.ACTION_WIRELESS_SETTINGS
        )
        for (action in screens) {
            try {
                val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    intent.putExtra("android.provider.extra.SUB_ID", subId)
                }
                startActivity(intent)
                return true
            } catch (e: Exception) {
                // Not available on this phone, try the next screen.
            }
        }
        return false
    }
}
