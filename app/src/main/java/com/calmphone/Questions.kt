package com.calmphone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Random

data class Question(val text: String, val choices: List<String>, val answer: Int)

/**
 * Two sources of questions:
 *  1. gemini(): asks the Gemini API (needs your own API key in Settings).
 *  2. local(): generated on the phone, works offline, and never repeats exactly.
 * Both are multiple choice so answers can be checked without trusting free text.
 */
object Questions {
    private val rnd = Random()

    // ---------- Gemini ----------

    /** Blocking network call. Run on a background thread. Returns null on any problem. */
    fun gemini(c: Context): Question? {
        val key = Prefs.str(c, "geminiKey").trim()
        if (key.isEmpty()) return null
        val model = Prefs.str(c, "geminiModel", "gemini-2.5-flash").trim().ifEmpty { "gemini-2.5-flash" }
        val topics = listOf(
            "a logic puzzle", "mental arithmetic", "a lateral-thinking riddle", "pattern recognition",
            "a word puzzle", "science trivia", "world geography", "history trivia",
            "a probability puzzle", "a spatial reasoning puzzle"
        )
        val topic = topics[rnd.nextInt(topics.size)]
        val prompt = "Write one multiple-choice question that challenges the brain: $topic. " +
            "It must have exactly one objectively correct answer that you have double-checked, " +
            "and three plausible wrong answers. Keep it under 40 words. Random seed: ${rnd.nextInt(1000000)}. " +
            "Reply with JSON only, in this shape: " +
            "{\"question\":\"...\",\"choices\":[\"...\",\"...\",\"...\",\"...\"],\"answer\":0} " +
            "where answer is the index (0-3) of the correct choice."

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 1.0).put("responseMimeType", "application/json")
            )

        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 9000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) return null
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val text = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val j = JSONObject(cleaned)
            val q = j.getString("question").trim()
            val arr = j.getJSONArray("choices")
            val ans = j.getInt("answer")
            if (q.isEmpty() || arr.length() != 4 || ans !in 0..3) return null
            val list = ArrayList<String>()
            for (i in 0 until 4) {
                val s = arr.getString(i).trim()
                if (s.isEmpty()) return null
                list.add(s)
            }
            val correct = list[ans]
            if (list.distinct().size != 4) return null
            Collections.shuffle(list, rnd)
            return Question(q, list, list.indexOf(correct))
        } catch (e: Exception) {
            return null
        } finally {
            conn?.disconnect()
        }
    }

    // ---------- Local generators ----------

    fun local(): Question = when (rnd.nextInt(7)) {
        0 -> arithmetic()
        1 -> sequence()
        2 -> oddOneOut()
        3 -> reverseWord()
        4 -> weekday()
        5 -> vowels()
        else -> secondLargest()
    }

    private fun finish(text: String, correct: String, wrong: List<String>): Question {
        val all = ArrayList<String>()
        all.add(correct)
        all.addAll(wrong.distinct().filter { it != correct }.take(3))
        Collections.shuffle(all, rnd)
        return Question(text, all, all.indexOf(correct))
    }

    private fun numeric(text: String, correct: Int): Question {
        val opts = LinkedHashSet<Int>()
        opts.add(correct)
        while (opts.size < 4) {
            val d = rnd.nextInt(9) + 1
            opts.add(if (rnd.nextBoolean()) correct + d else correct - d)
        }
        return finish(text, correct.toString(), opts.filter { it != correct }.map { it.toString() })
    }

    private fun arithmetic(): Question {
        val a = rnd.nextInt(30) + 12
        val b = rnd.nextInt(8) + 3
        val c = rnd.nextInt(50) + 10
        return if (rnd.nextBoolean()) {
            numeric("What is ($a × $b) − $c?", a * b - c)
        } else {
            numeric("What is $a × $b + $c?", a * b + c)
        }
    }

    private fun sequence(): Question {
        if (rnd.nextBoolean()) {
            val start = rnd.nextInt(15) + 2
            val step = rnd.nextInt(7) + 3
            val t = (0..4).map { start + step * it }
            return numeric("What comes next?  ${t.joinToString(", ")}, …", start + step * 5)
        }
        val start = rnd.nextInt(9) + 1
        var v = start
        val t = ArrayList<Int>()
        for (i in 1..5) {
            t.add(v)
            v += i
        }
        return numeric("What comes next?  ${t.joinToString(", ")}, …", v)
    }

    private val groups = listOf(
        listOf("apple", "banana", "cherry", "grape", "mango", "pear"),
        listOf("dog", "cat", "horse", "rabbit", "sheep", "goat"),
        listOf("hammer", "wrench", "saw", "drill", "chisel", "pliers"),
        listOf("Mars", "Venus", "Jupiter", "Saturn", "Neptune", "Mercury"),
        listOf("piano", "violin", "flute", "guitar", "drums", "cello"),
        listOf("river", "lake", "ocean", "stream", "pond", "sea")
    )

    private fun oddOneOut(): Question {
        val gi = rnd.nextInt(groups.size)
        var oi = rnd.nextInt(groups.size)
        while (oi == gi) oi = rnd.nextInt(groups.size)
        val pool = ArrayList(groups[gi])
        Collections.shuffle(pool, rnd)
        val odd = groups[oi][rnd.nextInt(groups[oi].size)]
        return finish("Which one does not belong with the others?", odd, pool.take(3))
    }

    private val words = listOf(
        "planet", "garden", "window", "silver", "orange",
        "bridge", "castle", "pencil", "market", "forest"
    )

    private fun reverseWord(): Question {
        val w = words[rnd.nextInt(words.size)]
        val correct = w.reversed()
        val wrong = LinkedHashSet<String>()
        var tries = 0
        while (wrong.size < 3 && tries < 60) {
            tries++
            val ch = correct.toCharArray()
            val i = rnd.nextInt(ch.size - 1)
            val t = ch[i]
            ch[i] = ch[i + 1]
            ch[i + 1] = t
            val s = String(ch)
            if (s != correct) wrong.add(s)
        }
        return finish("Spell \"$w\" backwards.", correct, wrong.toList())
    }

    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private fun weekday(): Question {
        val i = rnd.nextInt(7)
        val n = rnd.nextInt(30) + 9
        val ans = days[(i + n) % 7]
        val others = ArrayList(days.filter { it != ans })
        Collections.shuffle(others, rnd)
        return finish("If today is ${days[i]}, what day will it be in $n days?", ans, others.take(3))
    }

    private val vowelWords = listOf(
        "education", "beautiful", "sequence", "adventure",
        "mountain", "umbrella", "triangle", "discovery"
    )

    private fun vowels(): Question {
        val w = vowelWords[rnd.nextInt(vowelWords.size)]
        val n = w.count { it in "aeiou" }
        return numeric("How many vowels (a, e, i, o, u) are in \"$w\"?", n)
    }

    private fun secondLargest(): Question {
        val nums = LinkedHashSet<Int>()
        while (nums.size < 4) nums.add(rnd.nextInt(90) + 10)
        val sorted = nums.sortedDescending()
        return finish(
            "Which is the second largest?  ${nums.joinToString(", ")}",
            sorted[1].toString(),
            listOf(sorted[0], sorted[2], sorted[3]).map { it.toString() }
        )
    }
}
