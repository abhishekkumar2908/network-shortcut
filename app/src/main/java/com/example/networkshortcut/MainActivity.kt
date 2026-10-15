package com.example.networkshortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast

/**
 * No screen of its own: tapping the icon opens the mobile network settings of
 * the SIM that carries mobile data, where "Preferred network type" is, and closes.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val hasSim = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID

        val candidates = listOf(
            // The SIM's own mobile network page (Android 12+).
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$MobileNetworkActivity"
            ),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )

        var opened = false
        for (intent in candidates) {
            try {
                if (hasSim) intent.putExtra("android.provider.extra.SUB_ID", subId)
                startActivity(intent)
                opened = true
                break
            } catch (e: Exception) {
                // Not available on this phone, try the next one.
            }
        }

        if (!opened) {
            Toast.makeText(this, "Network settings not available on this phone", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
