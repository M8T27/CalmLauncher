package com.calmphone

import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers so every screen is built in code with the same black-and-white look. */
object Ui {
    val FG: Int = Color.parseColor("#EDEDED")
    val DIM: Int = Color.parseColor("#9A9A9A")
    val CARD: Int = Color.parseColor("#1E1E1E")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun lp(c: Context, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, top) }

    fun text(c: Context, s: String, size: Float = 16f, color: Int = FG): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    fun button(c: Context, s: String, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = s
        b.setAllCaps(false)
        b.setTextColor(FG)
        b.setBackgroundColor(CARD)
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        b.setOnClickListener { onClick() }
        return b
    }
}
