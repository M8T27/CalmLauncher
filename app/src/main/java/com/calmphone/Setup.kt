package com.calmphone

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Checks for the one-time setup steps. */
object Setup {
    fun accessibilityOn(c: Context): Boolean {
        val cn = ComponentName(c, GuardService::class.java)
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains(cn.flattenToString()) || s.contains(cn.flattenToShortString())
    }

    fun secureOn(c: Context): Boolean = ScreenFx.canWriteSecure(c)

    fun notifOn(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun adminOn(c: Context): Boolean {
        val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(c, AdminReceiver::class.java))
    }

    fun isDefaultHome(c: Context): Boolean {
        val ri = c.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return ri?.activityInfo?.packageName == c.packageName
    }
}
