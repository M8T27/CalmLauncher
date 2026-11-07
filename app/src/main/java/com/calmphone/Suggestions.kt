package com.calmphone

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val icon: Drawable, val category: Int)

/** Groups installed apps into the calm sections at the top of the home screen. */
object Suggestions {
    fun build(c: Context, apps: List<AppEntry>): LinkedHashMap<String, List<AppEntry>> {
        val used = HashSet<String>()

        fun pick(
            keywords: List<String>,
            pkgs: Set<String?> = emptySet(),
            cats: Set<Int> = emptySet()
        ): List<AppEntry> {
            val out = apps.filter { a ->
                a.pkg !in used &&
                    (a.pkg in pkgs || a.category in cats || keywords.any { a.label.contains(it, ignoreCase = true) })
            }
            used.addAll(out.map { it.pkg })
            return out
        }

        val m = LinkedHashMap<String, List<AppEntry>>()
        val talk = pick(emptyList(), setOf(Essentials.dialer(c), Essentials.sms(c), Essentials.contacts(c)))
        m["Talk"] = talk + pick(listOf("signal", "whatsapp", "telegram", "messages", "contacts", "phone"))
        m["Stay informed"] = pick(
            listOf("news", "weather", "reuters", "bbc", "npr"),
            emptySet(),
            setOf(ApplicationInfo.CATEGORY_NEWS)
        )
        m["Read & learn"] = pick(
            listOf(
                "kobo", "koreader", "kindle", "libby", "duolingo", "khan",
                "anki", "coursera", "books", "dictionary"
            )
        )
        m["Notes & utilities"] = pick(
            listOf(
                "note", "keep", "files", "file manager", "documents",
                "calendar", "calculator", "clock", "tasks", "settings"
            )
        )
        m["Focus & look"] = pick(listOf("opal", "paperize"))
        return m
    }
}
