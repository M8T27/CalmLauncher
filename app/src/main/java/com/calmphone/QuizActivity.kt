package com.calmphone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The gate. Answer N multiple-choice questions in a row. One wrong answer resets the run and
 * locks you out for 30 seconds, after which you get a fresh set.
 */
class QuizActivity : Activity() {
    companion object {
        const val MODE_UNLOCK = "unlock"
        const val MODE_BREAK = "break"
        const val MODE_SETTINGS = "settings"
        const val MODE_GUARD = "guard"

        fun intent(
            c: Context,
            mode: String,
            count: Int,
            pkg: String = "",
            minutes: Int = 0,
            next: String = "settings"
        ): Intent = Intent(c, QuizActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("mode", mode)
            .putExtra("count", count)
            .putExtra("pkg", pkg)
            .putExtra("minutes", minutes)
            .putExtra("next", next)
    }

    private var mode = MODE_UNLOCK
    private var needed = 3
    private var pkg = ""
    private var minutes = 0
    private var next = "settings"

    private var solved = 0
    private var current: Question? = null
    private var token = 0
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var choices: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: MODE_UNLOCK
        needed = intent.getIntExtra("count", 3).coerceAtLeast(1)
        pkg = intent.getStringExtra("pkg") ?: ""
        minutes = intent.getIntExtra("minutes", 0)
        next = intent.getStringExtra("next") ?: "settings"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 56), Ui.dp(this, 24), Ui.dp(this, 24))

        title = Ui.text(this, "", 14f, Ui.DIM)
        body = Ui.text(this, "", 22f)
        choices = LinearLayout(this)
        choices.orientation = LinearLayout.VERTICAL

        root.addView(title)
        root.addView(body, Ui.lp(this, 16))
        root.addView(choices, Ui.lp(this, 24))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        scroll.addView(root)
        setContentView(scroll)

        startOrWait()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Back always returns to the home screen; there is no skipping the quiz.")
    override fun onBackPressed() {
        goHome()
    }

    private fun label(): String = when (mode) {
        MODE_UNLOCK -> "Unlock " + appLabel(pkg)
        MODE_BREAK -> "Break · $minutes min"
        MODE_SETTINGS -> "Settings"
        else -> "Protection"
    }

    private fun appLabel(p: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
    } catch (e: Exception) {
        p
    }

    private fun startOrWait() {
        if (Gate.cooldownLeftMs(this) > 0) cooldown() else load()
    }

    private fun cooldown() {
        token++
        current = null
        choices.removeAllViews()
        title.text = "Wrong answer"
        val tick = object : Runnable {
            override fun run() {
                val left = Gate.cooldownLeftMs(this@QuizActivity)
                if (left <= 0) {
                    solved = 0
                    load()
                } else {
                    body.text = "Wait ${(left + 999) / 1000}s, then try a fresh set of $needed."
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(tick)
    }

    private fun load() {
        token++
        val my = token
        current = null
        choices.removeAllViews()
        title.text = "${label()} · question ${solved + 1} of $needed"
        body.text = "Thinking of a question…"
        val app = applicationContext
        Thread {
            val q = Questions.gemini(app) ?: Questions.local()
            runOnUiThread {
                if (my == token && !isFinishing) show(q)
            }
        }.start()
    }

    private fun show(q: Question) {
        current = q
        body.text = q.text
        choices.removeAllViews()
        q.choices.forEachIndexed { i, s ->
            choices.addView(Ui.button(this, s) { answer(i) }, Ui.lp(this, 10))
        }
    }

    private fun answer(i: Int) {
        val q = current ?: return
        current = null // ignore double taps
        if (i == q.answer) {
            solved++
            if (solved >= needed) success() else load()
        } else {
            Gate.startCooldown(this)
            solved = 0
            cooldown()
        }
    }

    private fun success() {
        when (mode) {
            MODE_UNLOCK -> {
                Gate.unlock(this, pkg)
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
            MODE_BREAK -> {
                Gate.startBreak(this, minutes)
                Toast.makeText(this, "Break started: $minutes min", Toast.LENGTH_SHORT).show()
                if (Prefs.bool(this, "opalAssist", true)) OpalBridge.open(this)
            }
            MODE_SETTINGS -> {
                Gate.grantSettingsPass(this)
                if (next == "settings") startActivity(Intent(this, SettingsActivity::class.java))
            }
            MODE_GUARD -> {
                Gate.grantUninstallPass(this)
                Toast.makeText(this, "You have 5 minutes.", Toast.LENGTH_LONG).show()
            }
        }
        GuardService.instance?.tick()
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }
}
