package com.example.networkshortcut

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Taps through the "Preferred network type" screen in Settings for you.
 * It only listens to the Settings apps (see accessibility_service_config.xml)
 * and does nothing unless the popup has asked for a switch in the last 20 seconds.
 */
class NetworkAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var target: String? = null // "4G" or "5G"
        @Volatile private var startedAt = 0L
        @Volatile private var rowClickedAt = 0L
        @Volatile private var scrollTries = 0

        fun request(label: String) {
            target = label
            startedAt = SystemClock.elapsedRealtime()
            rowClickedAt = 0L
            scrollTries = 0
        }

        fun cancel() {
            target = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val want = target ?: return
        if (SystemClock.elapsedRealtime() - startedAt > 20_000) {
            target = null
            return
        }
        val root = rootInActiveWindow ?: return

        if (rowClickedAt == 0L) {
            // Step 1: open the "Preferred network type" row.
            val row = root.findAccessibilityNodeInfosByText("Preferred network type").firstOrNull()
            if (row != null) {
                if (clickNode(row)) rowClickedAt = SystemClock.elapsedRealtime()
            } else {
                scrollOnce(root)
            }
        } else if (SystemClock.elapsedRealtime() - rowClickedAt > 600) {
            // Step 2: the list of types is showing, pick the wanted one.
            val option = findOption(root, want)
            if (option != null && clickNode(option)) {
                target = null
                Toast.makeText(applicationContext, "Switched to $want", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 700)
            }
        }
    }

    override fun onInterrupt() {}

    private fun findOption(root: AccessibilityNodeInfo, want: String): AccessibilityNodeInfo? {
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
        // Prefer real list choices (radio buttons) over other text that happens to match.
        return matches.firstOrNull {
            it.isCheckable || (it.className?.toString() ?: "").let { c ->
                c.contains("Radio") || c.contains("Checked")
            }
        } ?: matches.firstOrNull()
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
