package com.calmphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/** Grayscale (system color filter) and the dim / warm / soft overlays. */
object ScreenFx {
    private const val EN = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    fun canWriteSecure(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Turns the system "monochromacy" color correction on or off. Needs the WRITE_SECURE_SETTINGS
     * permission (one-time adb grant). We only switch it off again if we were the one who switched it on,
     * so a real color-correction setting is never clobbered.
     */
    fun setGrayscale(c: Context, on: Boolean): Boolean {
        if (!canWriteSecure(c)) return false
        return try {
            val r = c.contentResolver
            val enabled = Settings.Secure.getInt(r, EN, 0) == 1
            val mode = Settings.Secure.getInt(r, MODE, -1)
            if (on) {
                if (mode != 0) Settings.Secure.putInt(r, MODE, 0)
                if (!enabled) Settings.Secure.putInt(r, EN, 1)
                Prefs.put(c, "grayOwned", true)
            } else if (Prefs.bool(c, "grayOwned", false)) {
                if (enabled && mode == 0) Settings.Secure.putInt(r, EN, 0)
                Prefs.put(c, "grayOwned", false)
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

/**
 * One full-screen, untouchable accessibility overlay holding three tint layers:
 *  - dim:  black, goes beyond the system's minimum brightness
 *  - warm: amber, the blue-light filter (only visible when grayscale is off)
 *  - soft: gray wash that makes colors paler but still pleasant
 */
class FilterOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root = FrameLayout(ctx)
    private val dim = View(ctx)
    private val warm = View(ctx)
    private val soft = View(ctx)
    private var added = false

    private fun full() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )

    init {
        root.addView(soft, full())
        root.addView(warm, full())
        root.addView(dim, full())
    }

    private fun a(pct: Int): Int = (pct.coerceIn(0, 100) * 255) / 100

    fun update(dimPct: Int, warmPct: Int, softPct: Int) {
        dim.setBackgroundColor(Color.argb(a(dimPct), 0, 0, 0))
        warm.setBackgroundColor(Color.argb(a(warmPct), 255, 138, 0))
        soft.setBackgroundColor(Color.argb(a(softPct), 128, 128, 128))
        val any = dimPct > 0 || warmPct > 0 || softPct > 0
        try {
            if (any && !added) {
                wm.addView(root, params())
                added = true
            } else if (!any && added) {
                wm.removeView(root)
                added = false
            }
        } catch (e: Exception) {
        }
    }

    fun remove() {
        try {
            if (added) wm.removeView(root)
        } catch (e: Exception) {
        }
        added = false
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )
}
