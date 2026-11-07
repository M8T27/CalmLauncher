package com.calmphone

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build

/** The always-on notification: your control panel in the shade. */
object Notifications {
    private const val CH = "control"
    private const val ID = 7

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            val ch = NotificationChannel(CH, "Control panel", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun action(c: Context, label: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notif), label, pi).build()

    fun update(c: Context, phase: Schedule.Phase) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val b = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentTitle("Calm Launcher")
        b.setContentIntent(
            PendingIntent.getActivity(
                c, 3,
                Intent(c, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags
            )
        )

        if (phase == Schedule.Phase.BEDTIME) {
            b.setContentText("Bedtime until ${Schedule.label(c, Schedule.wake(c))}. Alarms and calls still work.")
        } else if (Gate.breakActive(c)) {
            val until = Prefs.long(c, "breakUntil")
            b.setContentText("Break in progress")
            b.setShowWhen(true)
            b.setWhen(until)
            b.setUsesChronometer(true)
            b.setChronometerCountDown(true)
            b.addAction(
                action(
                    c, "End break",
                    PendingIntent.getBroadcast(
                        c, 2,
                        Intent(c, ControlReceiver::class.java).setAction(ControlReceiver.END_BREAK),
                        flags
                    )
                )
            )
        } else {
            if (phase == Schedule.Phase.WINDDOWN) {
                b.setContentText("Winding down. Bedtime at ${Schedule.label(c, Schedule.bed(c))}.")
            } else {
                b.setContentText("Locks, dimming and bedtime are active.")
            }
            b.addAction(
                action(
                    c, "Break",
                    PendingIntent.getActivity(
                        c, 1,
                        Intent(c, BreakActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        flags
                    )
                )
            )
            if (phase == Schedule.Phase.DAY) {
                b.addAction(
                    action(
                        c, "Color 5 min",
                        PendingIntent.getBroadcast(
                            c, 4,
                            Intent(c, ControlReceiver::class.java).setAction(ControlReceiver.COLOR),
                            flags
                        )
                    )
                )
            }
        }
        c.getSystemService(NotificationManager::class.java).notify(ID, b.build())
    }
}
