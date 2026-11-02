package com.studyontv.tv

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class WatchAccessibilityService : AccessibilityService() {
    private var gate: GateOverlay? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (pkg != null && (pkg.contains("inputmethod") || pkg.contains("keyboard"))) return
            if (!GateService.hasGate() || pkg != packageName) Foreground.update(pkg)
            GateService.onForegroundChanged(this, pkg)
        }
    }
    override fun onInterrupt() = Unit
    override fun onServiceConnected() {
        instance = this
        connected = true
        gate = GateOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        if (Prefs.onboarded(this)) GateService.start(this)
    }
    override fun onDestroy() {
        gate?.hide(); gate = null
        instance = null
        connected = false
        Foreground.clearExternal()
        super.onDestroy()
    }

    companion object {
        private var instance: WatchAccessibilityService? = null
        var connected = false
            private set
        fun hasGate(): Boolean = instance?.gate?.isShowing == true
        fun showGate(reason: String): Boolean = instance?.gate?.show(reason) == true
        fun hideGate() { instance?.gate?.hide() }
        fun suspendGate() { instance?.gate?.suspend() }
    }
}
