package com.calmphone

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * The heart of the app. Runs as an accessibility service so it can see which app just came forward.
 *  - locked app opened          -> quiz
 *  - non-essential app at bedtime -> bounced home
 *  - Settings / installer screen showing this app's name -> 5 question quiz (uninstall guard)
 *  - every 15 s: grayscale, dim/warm/soft overlays, break expiry, control-panel notification
 */
class GuardService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: GuardService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: FilterOverlay? = null
    private var lastPhase = Schedule.Phase.DAY
    private var lastQuizAt = 0L
    private var lastGuardAt = 0L
    private var lastToastAt = 0L
    private var opalTapForBreak = 0L
    private var allowedCache: Set<String> = emptySet()
    private var allowedAt = 0L

    private val guarded = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.vending"
    )

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 15_000)
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) Gate.clearUnlocks() else tick()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = FilterOverlay(this)
        Prefs.put(this, "serviceRunning", true)
        Notifications.ensureChannel(this)
        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_OFF)
        f.addAction(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screen, f)
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    private fun shutdown() {
        handler.removeCallbacks(ticker)
        try {
            unregisterReceiver(screen)
        } catch (e: Exception) {
        }
        overlay?.remove()
        overlay = null
        if (instance === this) instance = null
        Prefs.put(this, "serviceRunning", false)
        ScreenFx.setGrayscale(this, false) // give the person their colors back if the service is switched off
        Notifications.cancel(this)
    }

    // ---------------------------------------------------------------- events

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val ev = e ?: return
        val pkg = ev.packageName?.toString() ?: return
        when (ev.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == packageName) return
                guardScreen(pkg)
                enforce(pkg)
                opalAssist(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == packageName) return
                guardScreen(pkg)
                opalAssist(pkg)
            }
        }
    }

    private fun allowed(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - allowedAt > 30_000 || allowedCache.isEmpty()) {
            allowedCache = Essentials.alwaysAllowed(this)
            allowedAt = now
        }
        return allowedCache
    }

    /** Decide what to do about the app that just came to the front. */
    private fun enforce(pkg: String) {
        if (pkg == packageName || allowed().contains(pkg)) return
        val phase = Schedule.phase(this)

        if (phase == Schedule.Phase.BEDTIME && !Essentials.bedtimeOk(this).contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val now = SystemClock.uptimeMillis()
            if (now - lastToastAt > 4000) {
                lastToastAt = now
                Toast.makeText(
                    this,
                    "Bedtime until ${Schedule.label(this, Schedule.wake(this))}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        if (!Prefs.strSet(this, "lockedApps").contains(pkg)) return
        if (phase != Schedule.Phase.BEDTIME && Gate.breakActive(this)) return
        if (Gate.isUnlocked(pkg)) return
        launchQuiz(QuizActivity.MODE_UNLOCK, 3, pkg)
    }

    /** Re-check whatever is on screen right now (after a break ends or bedtime begins). */
    fun relock() {
        val p = rootInActiveWindow?.packageName?.toString() ?: return
        enforce(p)
    }

    private fun launchQuiz(mode: String, count: Int, pkg: String = "") {
        val now = SystemClock.uptimeMillis()
        if (now - lastQuizAt < 1200) return
        lastQuizAt = now
        try {
            startActivity(QuizActivity.intent(this, mode, count, pkg))
        } catch (e: Exception) {
        }
    }

    // ---------------------------------------------------------------- uninstall / switch-off guard

    private fun guardScreen(pkg: String) {
        if (pkg !in guarded) return
        if (!Prefs.bool(this, "guardOn", true) || Gate.uninstallPassValid(this)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastGuardAt < 700) return
        lastGuardAt = now
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, 0)
        val label = getString(R.string.app_name).lowercase()
        if (sb.toString().lowercase().contains(label)) {
            launchQuiz(QuizActivity.MODE_GUARD, 5)
        }
    }

    private fun collect(n: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 40 || sb.length > 20000) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) {
            val ch = n.getChild(i) ?: continue
            collect(ch, sb, depth + 1)
        }
    }

    // ---------------------------------------------------------------- experimental Opal assist

    private fun opalAssist(pkg: String) {
        if (!Prefs.bool(this, "opalAutoTap", false) || !Gate.breakActive(this)) return
        if (pkg != OpalBridge.pkg(this)) return
        val until = Prefs.long(this, "breakUntil")
        if (opalTapForBreak == until) return
        val root = rootInActiveWindow ?: return
        val node = findClickable(root, listOf("take a break", "pause", "unblock")) ?: return
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) opalTapForBreak = until
    }

    private fun findClickable(n: AccessibilityNodeInfo, words: List<String>): AccessibilityNodeInfo? {
        val t = ((n.text ?: "").toString() + " " + (n.contentDescription ?: "")).lowercase()
        if (words.any { t.contains(it) }) {
            var p: AccessibilityNodeInfo? = n
            while (p != null && !p.isClickable) p = p.parent
            if (p != null) return p
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val r = findClickable(c, words)
            if (r != null) return r
        }
        return null
    }

    // ---------------------------------------------------------------- periodic work

    fun tick() {
        val phase = Schedule.phase(this)

        // A break can never run into bedtime.
        if (phase == Schedule.Phase.BEDTIME && Prefs.long(this, "breakUntil") != 0L) Gate.endBreak(this)
        var relock = phase != lastPhase
        lastPhase = phase
        val breakUntil = Prefs.long(this, "breakUntil")
        if (breakUntil != 0L && breakUntil <= System.currentTimeMillis()) {
            Gate.endBreak(this)
            relock = true
        }

        val night = phase != Schedule.Phase.DAY
        val colorOn = phase == Schedule.Phase.DAY && Gate.colorActive(this)
        val nightWarm = night && Prefs.bool(this, "nightWarm", false)
        val gray = Prefs.bool(this, "grayscale", true) && !colorOn && !nightWarm
        ScreenFx.setGrayscale(this, gray)

        val soft = if (gray) 0 else Prefs.int(this, "softPct", 12)
        val dim = if (night) Prefs.int(this, "nightDim", 60) else Prefs.int(this, "dayDim", 0)
        val warm = if (nightWarm) Prefs.int(this, "nightWarmPct", 40) else 0
        overlay?.update(dim, warm, soft)

        Notifications.update(this, phase)
        if (relock) relock()
    }
}
