package com.calmphone

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Pick a break length in 5-minute steps. Each 5 minutes costs one quiz question. */
class BreakActivity : Activity() {
    private var minutes = 5
    private lateinit var value: TextView
    private lateinit var info: TextView
    private lateinit var start: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 56), Ui.dp(this, 24), Ui.dp(this, 24))

        root.addView(Ui.text(this, "Take a break", 28f))
        value = Ui.text(this, "", 48f)
        info = Ui.text(this, "", 15f, Ui.DIM)
        root.addView(value, Ui.lp(this, 32))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val half = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        row.addView(Ui.button(this, "− 5") { change(-5) }, half())
        row.addView(Ui.button(this, "+ 5") { change(5) }, half())
        root.addView(row, Ui.lp(this, 16))

        root.addView(info, Ui.lp(this, 16))
        start = Ui.button(this, "Answer questions to start") { begin() }
        root.addView(start, Ui.lp(this, 24))

        setContentView(root)
        refresh()
    }

    private fun change(delta: Int) {
        val cap = Schedule.maxBreakMinutes(this)
        minutes = (minutes + delta).coerceIn(5, maxOf(5, cap))
        refresh()
    }

    private fun refresh() {
        val cap = Schedule.maxBreakMinutes(this)
        if (cap >= 5) minutes = minutes.coerceIn(5, cap)
        value.text = "$minutes min"
        val q = minutes / 5
        if (Gate.breakActive(this)) {
            info.text = "A break is already running."
            start.isEnabled = false
        } else if (cap < 5) {
            info.text = if (Schedule.phase(this) == Schedule.Phase.BEDTIME) {
                "Breaks are off during bedtime."
            } else {
                "Bedtime is too close for a break."
            }
            start.isEnabled = false
        } else {
            info.text = "$q question${if (q > 1) "s" else ""} to answer (one per 5 minutes). " +
                "Longest break right now: $cap min."
            start.isEnabled = true
        }
    }

    private fun begin() {
        startActivity(QuizActivity.intent(this, QuizActivity.MODE_BREAK, minutes / 5, minutes = minutes))
        finish()
    }
}
