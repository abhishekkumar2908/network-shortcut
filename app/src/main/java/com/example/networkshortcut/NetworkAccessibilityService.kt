package com.example.networkshortcut

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Switches 4G / 5G by tapping through the "Preferred network type" screen in Settings.
 * While it works, a full-screen "Switching..." cover hides Settings, and afterwards it
 * sends you back to the home screen. It only looks at the Settings apps (see
 * accessibility_service_config.xml) and only after you pick 4G or 5G in the popup.
 */
class NetworkAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: NetworkAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var target: String? = null
    private var rowClicked = false
    private var rowClickedAt = 0L
    private var scrollTries = 0
    private var cover: View? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        removeCover()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Called by the popup right before it opens the network settings. */
    fun start(label: String) {
        target = label
        rowClicked = false
        rowClickedAt = 0L
        scrollTries = 0
        showCover(label)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ tick() }, 400)
        handler.postDelayed({ giveUp() }, 20_000)
    }

    private fun tick() {
        val want = target ?: return
        val root = rootInActiveWindow
        if (root != null) {
            val now = SystemClock.elapsedRealtime()
            if (!rowClicked) {
                val row = root.findAccessibilityNodeInfosByText("Preferred network type").firstOrNull()
                if (row != null) {
                    if (clickNode(row)) {
                        rowClicked = true
                        rowClickedAt = now
                    }
                } else {
                    scrollOnce(root)
                }
            } else if (now - rowClickedAt > 500) {
                val option = findOption(root, want, now - rowClickedAt > 2500)
                if (option != null && clickNode(option)) {
                    done(want)
                    return
                }
            }
        }
        handler.postDelayed({ tick() }, 300)
    }

    private fun done(label: String) {
        target = null
        handler.removeCallbacksAndMessages(null)
        Toast.makeText(applicationContext, "Switched to $label", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ removeCover() }, 500)
        }, 500)
    }

    /** Could not finish in time: uncover Settings so you can pick it by hand. */
    private fun giveUp() {
        if (target == null) return
        target = null
        handler.removeCallbacksAndMessages(null)
        removeCover()
        Toast.makeText(
            applicationContext,
            "Couldn't switch automatically. Pick Preferred network type yourself.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showCover(label: String) {
        removeCover()
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2121212"))
            addView(TextView(context).apply {
                text = "Switching to $label..."
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
            cover = view
        } catch (e: Exception) {
            // Without the cover Settings is just visible for a moment; the switch still works.
        }
    }

    private fun removeCover() {
        cover?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (e: Exception) {
            }
        }
        cover = null
    }

    private fun findOption(root: AccessibilityNodeInfo, want: String, loose: Boolean): AccessibilityNodeInfo? {
        val all = mutableListOf<AccessibilityNodeInfo>()
        collect(root, all)
        val matches = all.filter { node ->
            val t = node.text?.toString() ?: ""
            if (t.contains("Preferred network type", ignoreCase = true)) {
                false
            } else if (want == "5G") {
                t.contains("5G")
            } else {
                (t.contains("4G") || t.contains("LTE", ignoreCase = true)) && !t.contains("5G")
            }
        }
        // Real list choices are radio buttons; other matching text could be the row's summary.
        val radio = matches.firstOrNull {
            it.isCheckable || (it.className?.toString() ?: "").let { c ->
                c.contains("Radio") || c.contains("Checked")
            }
        }
        return radio ?: if (loose) matches.firstOrNull() else null
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        out.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun scrollOnce(root: AccessibilityNodeInfo) {
        if (scrollTries >= 6) return
        val all = mutableListOf<AccessibilityNodeInfo>()
        collect(root, all)
        val scrollable = all.firstOrNull { it.isScrollable } ?: return
        scrollTries++
        scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
}
