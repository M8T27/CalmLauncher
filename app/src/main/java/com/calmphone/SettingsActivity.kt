package com.calmphone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch

/**
 * Everything is configured here. Once protection is running, opening this screen costs a 3-question quiz,
 * so you can't quietly switch your own rules off in a weak moment.
 */
class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.bool(this, "serviceRunning", false) && !Gate.settingsPassValid(this)) {
            startActivity(QuizActivity.intent(this, QuizActivity.MODE_SETTINGS, 3))
            finish()
            return
        }
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 40), Ui.dp(this, 20), Ui.dp(this, 40))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        scroll.addView(root)
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) renderStatus()
    }

    private fun header(s: String) {
        root.addView(Ui.text(this, s.uppercase(), 13f, Ui.DIM), Ui.lp(this, 32))
    }

    private fun build() {
        root.addView(Ui.text(this, "Calm Launcher", 28f))

        header("Setup")
        status = LinearLayout(this)
        status.orientation = LinearLayout.VERTICAL
        root.addView(status)
        renderStatus()

        header("Screen")
        switchRow("Grayscale", "grayscale", true)
        slider("Softer colors when color is on", "softPct", 0, 40, 12, "%")
        slider("Extra dimming during the day", "dayDim", 0, 60, 0, "%")
        slider("Extra dimming at night", "nightDim", 0, 85, 60, "%")
        switchRow("At night use a warm blue-light filter instead of grayscale", "nightWarm", false)
        slider("Warm filter strength", "nightWarmPct", 10, 60, 40, "%")

        header("Bedtime")
        switchRow("Bedtime on", "bedtimeOn", true)
        timeRow("Bedtime", "bedMin", 22 * 60 + 30)
        timeRow("Wake up", "wakeMin", 6 * 60 + 30)
        slider("Wind-down before bedtime", "windDown", 0, 180, 60, " min")

        header("Locks")
        slider("Stay unlocked after the quiz", "unlockMinutes", 1, 60, 10, " min")
        switchRow("Ask questions before this app can be removed or switched off", "guardOn", true)

        header("Questions from Gemini (optional)")
        field("Gemini API key (free from aistudio.google.com)", "geminiKey", "", true)
        field("Gemini model name", "geminiModel", "gemini-2.5-flash", false)

        header("Home screen")
        val names = Engines.names.keys.toList() + "Custom"
        val pick = Ui.button(this, "Search engine: ${Prefs.str(this, "engine", "DuckDuckGo")}") {}
        pick.setOnClickListener {
            AlertDialog.Builder(this)
                .setItems(names.toTypedArray()) { _, i ->
                    Prefs.put(this, "engine", names[i])
                    pick.text = "Search engine: ${names[i]}"
                }
                .show()
        }
        root.addView(pick, Ui.lp(this, 10))
        field("Custom search URL (ends with ?q=)", "engineCustom", "https://duckduckgo.com/?q=", false)

        header("Opal")
        switchRow("Open Opal when a break starts", "opalAssist", true)
        switchRow("Experimental: try to tap Opal's pause button during a break", "opalAutoTap", false)
        field("Opal package name (leave empty to auto-detect)", "opalPkg", "", false)
    }

    // ------------------------------------------------------------ setup checklist

    private fun renderStatus() {
        status.removeAllViews()
        fun row(ok: Boolean, label: String, actionLabel: String, action: () -> Unit) {
            status.addView(Ui.text(this, (if (ok) "[x]  " else "[ ]  ") + label, 16f), Ui.lp(this, 10))
            if (!ok) status.addView(Ui.button(this, actionLabel, action), Ui.lp(this, 4))
        }
        row(
            Setup.accessibilityOn(this),
            "Accessibility service (locks, bedtime, uninstall guard)",
            "Open accessibility settings"
        ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row(Setup.secureOn(this), "Grayscale permission", "Show the one-time adb command") { showAdb() }
        row(Setup.notifOn(this), "Notifications (control panel)", "Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        row(Setup.adminOn(this), "Device admin (extra step before uninstalling)", "Activate") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, AdminReceiver::class.java)
                )
            )
        }
        row(Setup.isDefaultHome(this), "Set as your home app", "Choose home app") {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun showAdb() {
        AlertDialog.Builder(this)
            .setTitle("One-time permission")
            .setMessage(
                "Android only lets an app switch the grayscale filter if you grant this once from a computer " +
                    "with USB debugging turned on:\n\n" +
                    "adb shell pm grant com.calmphone android.permission.WRITE_SECURE_SETTINGS\n\n" +
                    "Nothing else needs a computer."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ------------------------------------------------------------ row builders

    private fun switchRow(label: String, key: String, def: Boolean) {
        val sw = Switch(this)
        sw.text = label
        sw.setTextColor(Ui.FG)
        sw.textSize = 16f
        sw.isChecked = Prefs.bool(this, key, def)
        sw.setOnCheckedChangeListener { _, v ->
            Prefs.put(this, key, v)
            GuardService.instance?.tick()
        }
        root.addView(sw, Ui.lp(this, 12))
    }

    private fun slider(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String) {
        val t = Ui.text(this, "", 16f)
        val cur = Prefs.int(this, key, def)
        t.text = "$label: $cur$unit"
        val bar = SeekBar(this)
        bar.max = hi - lo
        bar.progress = cur - lo
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + lo
                t.text = "$label: $v$unit"
                if (fromUser) Prefs.put(this@SettingsActivity, key, v)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                GuardService.instance?.tick()
            }
        })
        root.addView(t, Ui.lp(this, 14))
        root.addView(bar)
    }

    private fun timeRow(label: String, key: String, def: Int) {
        val b = Ui.button(this, "") {}
        val refresh = { b.text = "$label: ${Schedule.label(this, Prefs.int(this, key, def))}" }
        refresh()
        b.setOnClickListener {
            val v = Prefs.int(this, key, def)
            TimePickerDialog(
                this,
                { _, h, m ->
                    Prefs.put(this, key, h * 60 + m)
                    refresh()
                    GuardService.instance?.tick()
                },
                v / 60, v % 60, DateFormat.is24HourFormat(this)
            ).show()
        }
        root.addView(b, Ui.lp(this, 10))
    }

    private fun field(label: String, key: String, def: String, secret: Boolean) {
        root.addView(Ui.text(this, label, 14f, Ui.DIM), Ui.lp(this, 14))
        val e = EditText(this)
        e.setText(Prefs.str(this, key, def))
        e.setTextColor(Ui.FG)
        e.setHintTextColor(Ui.DIM)
        e.isSingleLine = true
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                Prefs.put(this@SettingsActivity, key, s?.toString() ?: "")
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        root.addView(e)
    }
}
