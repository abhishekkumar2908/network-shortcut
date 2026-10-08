package com.example.networkshortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings

/**
 * Popup that shows which SIM is used for mobile data and asks for 4G or 5G.
 *
 * Android does not let normal apps change the preferred network type, so the
 * choice opens that SIM's own mobile network settings, where it takes one tap.
 */
class MainActivity : Activity() {

    private var subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Which SIM carries mobile data right now?
        subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val carrier = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            val tm = (getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                .createForSubscriptionId(subId)
            tm.simOperatorName.ifBlank { tm.networkOperatorName }
        } else ""

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
            text = "Select network type"
            setPadding(0, pad / 2, 0, pad / 2)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(choiceButton("4G"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(choiceButton("5G"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        setContentView(root)
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.CENTER)
    }

    private fun choiceButton(label: String) = Button(this).apply {
        text = label
        setOnClickListener { openNetworkSettings(label) }
    }

    /** Opens this SIM's mobile network settings, trying the closest screen first. */
    private fun openNetworkSettings(label: String) {
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
                Toast.makeText(
                    applicationContext,
                    "Set Preferred network type to $label",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return
            } catch (e: Exception) {
                // Not available on this phone, try the next screen.
            }
        }
        Toast.makeText(this, "Network settings not available on this phone", Toast.LENGTH_SHORT).show()
    }
}
