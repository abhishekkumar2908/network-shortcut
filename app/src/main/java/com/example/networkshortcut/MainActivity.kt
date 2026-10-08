package com.example.networkshortcut

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Tiny launcher for the screens where "Preferred network type" (4G / 5G) lives.
 * Android does not let normal apps change that setting directly, so this just
 * gets you there in one tap.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Switch 4G / 5G"
            textSize = 24f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Tap below, then choose \"Preferred network type\"."
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })

        root.addView(Button(this).apply {
            text = "Open mobile network settings"
            setOnClickListener {
                open(
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                    Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            }
        })

        root.addView(Button(this).apply {
            text = "Open phone info (advanced)"
            setOnClickListener {
                open(
                    Intent().setClassName("com.android.settings", "com.android.settings.RadioInfo"),
                    Intent().setClassName("com.android.phone", "com.android.phone.settings.RadioInfo")
                )
            }
        })

        setContentView(root)
    }

    /** Tries each intent in order and launches the first one this phone accepts. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // Not available on this phone, try the next one.
            }
        }
        Toast.makeText(this, "Not available on this phone", Toast.LENGTH_SHORT).show()
    }
}
