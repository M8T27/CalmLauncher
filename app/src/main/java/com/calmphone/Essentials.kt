package com.calmphone

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Work out which packages must never be locked or blocked (alarms, calls, keyboard, home). */
object Essentials {
    private fun clean(p: String?): String? = if (p == null || p == "android") null else p

    fun home(c: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    fun dialer(c: Context): String? = try {
        clean((c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage)
    } catch (e: Exception) {
        null
    }

    fun sms(c: Context): String? = try {
        clean(Telephony.Sms.getDefaultSmsPackage(c))
    } catch (e: Exception) {
        null
    }

    fun contacts(c: Context): String? = clean(
        c.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), 0
        )?.activityInfo?.packageName
    )

    fun clock(c: Context): String? = clean(
        c.packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            ?.activityInfo?.packageName
    )

    /** Never locked, never blocked - not even at bedtime. Includes the alarm clock and call screens. */
    fun alwaysAllowed(c: Context): Set<String> {
        val s = HashSet<String>()
        s.add(c.packageName)
        s.addAll(home(c))
        clock(c)?.let { s.add(it) }
        s.addAll(
            listOf(
                "com.android.systemui", "android", "com.android.emergency",
                "com.android.incallui", "com.samsung.android.incallui", "com.android.server.telecom"
            )
        )
        try {
            val imm = c.getSystemService(InputMethodManager::class.java)
            imm.enabledInputMethodList.forEach { s.add(it.packageName) }
        } catch (e: Exception) {
        }
        return s
    }

    /** Apps that stay usable at bedtime: calling, messaging, contacts, system settings. */
    fun bedtimeOk(c: Context): Set<String> {
        val s = HashSet<String>()
        dialer(c)?.let { s.add(it) }
        sms(c)?.let { s.add(it) }
        contacts(c)?.let { s.add(it) }
        s.add("com.android.settings")
        s.addAll(Prefs.strSet(c, "bedtimeAllowed"))
        return s
    }
}
