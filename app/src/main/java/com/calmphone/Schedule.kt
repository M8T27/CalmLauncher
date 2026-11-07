package com.calmphone

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * DAY       - normal.
 * WINDDOWN  - the hour before bedtime: night filters start, breaks are capped.
 * BEDTIME   - bed time until wake time: non-essential apps are blocked, no breaks.
 */
object Schedule {
    enum class Phase { DAY, WINDDOWN, BEDTIME }

    fun bed(c: Context): Int = Prefs.int(c, "bedMin", 22 * 60 + 30)
    fun wake(c: Context): Int = Prefs.int(c, "wakeMin", 6 * 60 + 30)

    private fun nowMin(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun inWindow(now: Int, start: Int, end: Int): Boolean =
        if (start <= end) now in start until end else (now >= start || now < end)

    fun phase(c: Context): Phase {
        if (!Prefs.bool(c, "bedtimeOn", true)) return Phase.DAY
        val now = nowMin()
        val bed = bed(c)
        val wake = wake(c)
        if (bed != wake && inWindow(now, bed, wake)) return Phase.BEDTIME
        val wind = Prefs.int(c, "windDown", 60)
        if (wind > 0) {
            val ws = ((bed - wind) % 1440 + 1440) % 1440
            if (inWindow(now, ws, bed)) return Phase.WINDDOWN
        }
        return Phase.DAY
    }

    fun minutesUntilBedtime(c: Context): Int = ((bed(c) - nowMin()) % 1440 + 1440) % 1440

    /** Longest break allowed right now, in 5-minute steps. A break can never run into bedtime. */
    fun maxBreakMinutes(c: Context): Int {
        if (phase(c) == Phase.BEDTIME) return 0
        if (!Prefs.bool(c, "bedtimeOn", true)) return 60
        return minOf(60, (minutesUntilBedtime(c) / 5) * 5)
    }

    fun label(c: Context, minuteOfDay: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        cal.set(Calendar.MINUTE, minuteOfDay % 60)
        return DateFormat.getTimeFormat(c).format(cal.time)
    }

    fun labelMillis(c: Context, ms: Long): String = DateFormat.getTimeFormat(c).format(Date(ms))
}
