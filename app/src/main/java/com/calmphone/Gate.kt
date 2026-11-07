package com.calmphone

import android.content.Context
import android.content.Intent

/** Runtime state for unlock windows, cooldowns, breaks and passes. */
object Gate {
    private val unlocked = HashMap<String, Long>()
    private const val MIN = 60_000L

    private fun now() = System.currentTimeMillis()

    // --- per-app unlock after passing the quiz (cleared when the screen turns off) ---
    fun isUnlocked(pkg: String): Boolean = (unlocked[pkg] ?: 0L) > now()
    fun unlock(c: Context, pkg: String) {
        unlocked[pkg] = now() + Prefs.int(c, "unlockMinutes", 10) * MIN
    }
    fun clearUnlocks() {
        unlocked.clear()
    }

    // --- 30 second wrong-answer cooldown (persisted so leaving the screen doesn't skip it) ---
    fun cooldownLeftMs(c: Context): Long = maxOf(0L, Prefs.long(c, "cooldownUntil") - now())
    fun startCooldown(c: Context) {
        Prefs.put(c, "cooldownUntil", now() + 30_000L)
    }

    // --- passes ---
    fun settingsPassValid(c: Context): Boolean = Prefs.long(c, "settingsPassUntil") > now()
    fun grantSettingsPass(c: Context) {
        Prefs.put(c, "settingsPassUntil", now() + 10 * MIN)
    }
    fun uninstallPassValid(c: Context): Boolean = Prefs.long(c, "uninstallPassUntil") > now()
    fun grantUninstallPass(c: Context) {
        Prefs.put(c, "uninstallPassUntil", now() + 5 * MIN)
    }

    // --- breaks ---
    fun breakActive(c: Context): Boolean = Prefs.long(c, "breakUntil") > now()
    fun startBreak(c: Context, minutes: Int) {
        Prefs.put(c, "breakUntil", now() + minutes * MIN)
    }
    fun endBreak(c: Context) {
        Prefs.put(c, "breakUntil", 0L)
    }

    // --- momentary color ---
    fun colorActive(c: Context): Boolean = Prefs.long(c, "colorUntil") > now()
    fun startColor(c: Context, minutes: Int) {
        Prefs.put(c, "colorUntil", now() + minutes * MIN)
    }
}

/**
 * Best-effort hook for the Opal app. Opal has no public API, so all we can do is open it
 * (so you can pause its block) and, optionally, try to tap its pause button (see GuardService).
 */
object OpalBridge {
    fun pkg(c: Context): String? {
        val override = Prefs.str(c, "opalPkg").trim()
        if (override.isNotEmpty()) return override
        val saved = Prefs.str(c, "opalPkgAuto")
        if (saved.isNotEmpty()) return saved
        val pm = c.packageManager
        val found = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).firstOrNull { it.loadLabel(pm).toString().contains("opal", ignoreCase = true) }
            ?.activityInfo?.packageName
        if (found != null) Prefs.put(c, "opalPkgAuto", found)
        return found
    }

    fun open(c: Context) {
        val p = pkg(c) ?: return
        val i = c.packageManager.getLaunchIntentForPackage(p) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(i)
        } catch (e: Exception) {
        }
    }
}
