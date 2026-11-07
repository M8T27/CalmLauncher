package com.calmphone

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * The minimalist home screen: clock, search bar, calm suggestions, then all apps as plain names
 * with black-and-white icons. Long-press an app to lock it, hide it, or allow it at bedtime.
 */
class HomeActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var colorBtn: TextView
    private lateinit var scroll: ScrollView

    private var apps: List<AppEntry> = emptyList()
    private var locked: Set<String> = emptySet()
    private var hidden: Set<String> = emptySet()
    private var showHidden = false

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.argb(150, 0, 0, 0)) // scrim so text is readable over any wallpaper
        root.setPadding(dp(24), dp(56), dp(24), dp(40))

        val clock = TextClock(this)
        clock.format12Hour = "h:mm"
        clock.format24Hour = "H:mm"
        clock.textSize = 60f
        clock.setTextColor(Ui.FG)
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

        val date = TextClock(this)
        date.format12Hour = "EEEE, MMMM d"
        date.format24Hour = "EEEE, MMMM d"
        date.textSize = 16f
        date.setTextColor(Ui.DIM)

        status = Ui.text(this, "", 14f, Ui.DIM)
        colorBtn = Ui.text(this, "Show color for 5 min", 14f, Ui.DIM)
        colorBtn.setPadding(0, dp(8), 0, dp(8))
        colorBtn.setOnClickListener {
            if (Schedule.phase(this) == Schedule.Phase.DAY) {
                Gate.startColor(this, 5)
                GuardService.instance?.tick()
                Toast.makeText(this, "Color on for 5 minutes", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Color is off in the evening", Toast.LENGTH_SHORT).show()
            }
        }

        search = EditText(this)
        search.hint = "Search apps or the web"
        search.setHintTextColor(Ui.DIM)
        search.setTextColor(Ui.FG)
        search.isSingleLine = true
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        search.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else {
                false
            }
        }

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)

        root.addView(clock)
        root.addView(date)
        root.addView(status, Ui.lp(this, 4))
        root.addView(colorBtn)
        root.addView(search, Ui.lp(this, 12))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        updateStatus()
        render()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN) {
            search.setText("")
            scroll.scrollTo(0, 0)
            showHidden = false
        }
    }

    @Deprecated("The home screen has nothing to go back to.")
    override fun onBackPressed() {
    }

    // ------------------------------------------------------------ data

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(intent, 0)
            .map { ri ->
                AppEntry(
                    ri.activityInfo.packageName,
                    ri.loadLabel(pm).toString(),
                    ri.loadIcon(pm),
                    ri.activityInfo.applicationInfo.category
                )
            }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun updateStatus() {
        val phase = Schedule.phase(this)
        status.text = when {
            phase == Schedule.Phase.BEDTIME -> "Bedtime until ${Schedule.label(this, Schedule.wake(this))}"
            Gate.breakActive(this) ->
                "Break until ${Schedule.labelMillis(this, Prefs.long(this, "breakUntil"))}"
            phase == Schedule.Phase.WINDDOWN -> "Winding down · bedtime ${Schedule.label(this, Schedule.bed(this))}"
            Gate.colorActive(this) ->
                "Color until ${Schedule.labelMillis(this, Prefs.long(this, "colorUntil"))}"
            else -> ""
        }
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        colorBtn.visibility = if (phase == Schedule.Phase.DAY) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------ rendering

    private fun render() {
        list.removeAllViews()
        locked = Prefs.strSet(this, "lockedApps")
        hidden = Prefs.strSet(this, "hiddenApps")

        if (!Setup.accessibilityOn(this)) {
            val t = Ui.text(this, "Setup isn't finished. Tap to continue →", 15f)
            t.setPadding(0, dp(8), 0, dp(16))
            t.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
            list.addView(t)
        }

        val visible = apps.filter { showHidden || it.pkg !in hidden }
        val q = search.text.toString().trim().lowercase()

        if (q.isEmpty()) {
            for ((title, items) in Suggestions.build(this, visible)) {
                if (items.isEmpty()) continue
                list.addView(section(title))
                items.forEach { list.addView(row(it)) }
            }
            val header = section(if (showHidden) "All apps (showing hidden)" else "All apps")
            header.setOnLongClickListener {
                requirePass {
                    showHidden = !showHidden
                    render()
                }
                true
            }
            list.addView(header)
            visible.forEach { list.addView(row(it)) }
        } else {
            visible.filter { it.label.lowercase().contains(q) }.forEach { list.addView(row(it)) }
            val web = Ui.text(this, "Search the web for \"$q\"", 17f, Ui.DIM)
            web.setPadding(0, dp(16), 0, dp(10))
            web.setOnClickListener { webSearch(q) }
            list.addView(web)
        }
    }

    private fun section(title: String): TextView {
        val t = Ui.text(this, title.uppercase(), 12f, Ui.DIM)
        t.setPadding(0, dp(24), 0, dp(4))
        return t
    }

    private fun gray(d: Drawable): Drawable {
        val copy = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
        copy.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        return copy
    }

    private fun row(a: AppEntry): View {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.setPadding(0, dp(10), 0, dp(10))

        val iv = ImageView(this)
        iv.setImageDrawable(gray(a.icon))
        line.addView(iv, LinearLayout.LayoutParams(dp(28), dp(28)))

        val name = a.label + (if (a.pkg in locked) "  · locked" else "")
        val tv = Ui.text(this, name, 19f)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.leftMargin = dp(16)
        line.addView(tv, lp)

        line.setOnClickListener { launch(a.pkg) }
        line.setOnLongClickListener {
            menu(a)
            true
        }
        return line
    }

    // ------------------------------------------------------------ actions

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun webSearch(q: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Engines.url(this) + Uri.encode(q))))
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitSearch() {
        val q = search.text.toString().trim()
        if (q.isEmpty()) return
        val m = apps.filter { (showHidden || it.pkg !in hidden) && it.label.contains(q, ignoreCase = true) }
        if (m.size == 1) launch(m[0].pkg) else webSearch(q)
    }

    /** Anything that weakens protection needs a quiz pass first (once protection is running). */
    private fun requirePass(action: () -> Unit) {
        if (!Prefs.bool(this, "serviceRunning", false) || Gate.settingsPassValid(this)) {
            action()
        } else {
            startActivity(QuizActivity.intent(this, QuizActivity.MODE_SETTINGS, 3, next = "home"))
        }
    }

    private fun menu(a: AppEntry) {
        val isLocked = a.pkg in locked
        val isHidden = a.pkg in hidden
        val bedOk = a.pkg in Prefs.strSet(this, "bedtimeAllowed")
        val items = arrayOf(
            if (isLocked) "Remove quiz lock" else "Lock behind quiz",
            if (isHidden) "Unhide" else "Hide from list",
            if (bedOk) "Block at bedtime" else "Allow at bedtime"
        )
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(items) { _, which ->
                val change = { key: String ->
                    Prefs.toggle(this, key, a.pkg)
                    render()
                }
                when (which) {
                    0 -> if (isLocked) requirePass { change("lockedApps") } else change("lockedApps")
                    1 -> if (isHidden) requirePass { change("hiddenApps") } else change("hiddenApps")
                    2 -> if (bedOk) change("bedtimeAllowed") else requirePass { change("bedtimeAllowed") }
                    else -> {}
                }
            }
            .show()
    }
}
