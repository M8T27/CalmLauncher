package com.calmphone

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the buttons on the control-panel notification. */
class ControlReceiver : BroadcastReceiver() {
    companion object {
        const val COLOR = "com.calmphone.COLOR"
        const val END_BREAK = "com.calmphone.END_BREAK"
    }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            COLOR -> if (Schedule.phase(c) == Schedule.Phase.DAY) Gate.startColor(c, 5)
            END_BREAK -> Gate.endBreak(c)
        }
        val svc = GuardService.instance
        if (svc != null) {
            svc.tick()
            if (i.action == END_BREAK) svc.relock()
        }
    }
}

/** Only exists so Android makes you deactivate it before the app can be uninstalled. */
class AdminReceiver : DeviceAdminReceiver()
