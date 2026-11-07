package com.calmphone

import android.content.Context

/** Tiny wrapper over SharedPreferences. Keys are plain strings used across the app. */
object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("calm", Context.MODE_PRIVATE)

    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun long(c: Context, k: String, d: Long = 0L): Long = sp(c).getLong(k, d)
    fun str(c: Context, k: String, d: String = ""): String = sp(c).getString(k, d) ?: d
    fun strSet(c: Context, k: String): Set<String> =
        (sp(c).getStringSet(k, emptySet()) ?: emptySet()).toSet()

    fun put(c: Context, k: String, v: Any) {
        val e = sp(c).edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is String -> e.putString(k, v)
            is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toSet())
            else -> {}
        }
        e.apply()
    }

    /** Adds the item if missing, removes it if present. Returns true if it was added. */
    fun toggle(c: Context, k: String, item: String): Boolean {
        val s = strSet(c, k).toMutableSet()
        val added = s.add(item)
        if (!added) s.remove(item)
        put(c, k, s)
        return added
    }
}

object Engines {
    val names: LinkedHashMap<String, String> = linkedMapOf(
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Google" to "https://www.google.com/search?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Startpage" to "https://www.startpage.com/do/search?q=",
        "Ecosia" to "https://www.ecosia.org/search?q=",
        "Bing" to "https://www.bing.com/search?q="
    )

    fun url(c: Context): String {
        val n = Prefs.str(c, "engine", "DuckDuckGo")
        if (n == "Custom") {
            return Prefs.str(c, "engineCustom", "https://duckduckgo.com/?q=")
        }
        return names[n] ?: names.values.first()
    }
}
