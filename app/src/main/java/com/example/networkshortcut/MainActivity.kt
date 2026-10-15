package com.example.networkshortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Tap the icon: a small popup with 4G and 5G appears over wherever you were.
 * Tap one and NetworkAccessibilityService does the switching.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        if (serviceEnabled()) {
            root.addView(TextView(this).apply {
                text = "Switch network to"
                textSize = 18f
                setPadding(0, 0, 0, pad / 2)
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (label in listOf("4G", "5G")) {
                row.addView(
                    Button(this).apply {
                        text = label
                        textSize = 22f
                        setOnClickListener { choose(label) }
                    },
                    LinearLayout.LayoutParams(0, (72 * dp).toInt(), 1f)
                )
            }
            root.addView(row)
        } else {
            root.addView(TextView(this).apply {
                text = "One-time setup: turn on \"Network Type\" in Accessibility " +
                    "(Downloaded apps or Installed apps).\n\n" +
                    "If it is greyed out: open the app's App info, tap the 3-dot menu, " +
                    "then Allow restricted settings."
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

    private fun choose(label: String) {
        waitForService(label, 0)
    }

    /** The service can take a moment to wake up right after the app starts. */
    private fun waitForService(label: String, tries: Int) {
        val service = NetworkAccessibilityService.instance
        if (service != null) {
            service.start(label)
            if (!openNetworkSettings()) {
                Toast.makeText(applicationContext, "Network settings not available on this phone", Toast.LENGTH_LONG).show()
            }
            finish()
        } else if (tries < 10) {
            handler.postDelayed({ waitForService(label, tries + 1) }, 300)
        } else {
            Toast.makeText(this, "Service not running. Turn Network Type off and on in Accessibility.", Toast.LENGTH_LONG).show()
        }
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/")
    }

    /** Opens the mobile network settings of the SIM that carries mobile data. */
    private fun openNetworkSettings(): Boolean {
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val hasSim = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val candidates = listOf(
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$MobileNetworkActivity"
            ),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in candidates) {
            try {
                if (hasSim) intent.putExtra("android.provider.extra.SUB_ID", subId)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                // Not available on this phone, try the next one.
            }
        }
        return false
    }
}
