package com.djape1.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ο σύνδεσμος πρόσκλησης χωρισμένος σε διεύθυνση σταθμού και κωδικό. */
data class Invite(val base: String, val token: String) {
    val stream: String get() = "$base/s/$token.mp3"
    /** Ποιότητα: "high" (προεπιλογή, αμετάβλητο URL), "medium", "low". */
    fun stream(quality: String): String = if (quality == "high") stream else "$stream?q=$quality"
    val now: String get() = "$base/n/$token"
    fun cover(v: String): String = "$base/c/$token?v=" + URLEncoder.encode(v, "UTF-8")
    val link: String get() = "$base/l/$token"
    val program: String get() = "$base/p/$token"
}

object LinkParser {
    private val RE = Regex("""^(https?://[^/\s?#]+)/(?:l|s|n|c)/([A-Za-z0-9_-]{20,64})(?:\.mp3)?/?(?:[?#].*)?$""")

    /** Δέχεται τον σύνδεσμο της σελίδας (/l/…) ή το stream URL (/s/….mp3). */
    fun parse(input: String?): Invite? {
        val s = input?.trim() ?: return null
        val m = RE.matchEntire(s) ?: return null
        return Invite(m.groupValues[1].trimEnd('/'), m.groupValues[2])
    }
}

/** Ένα κομμάτι του ιστορικού. */
data class Track(val id: String, val title: String, val artist: String?)

/** Μια γραμμή του προγράμματος του σταθμού. */
data class ProgramItem(val time: String?, val name: String, val desc: String?)

/** Μία ημέρα του εβδομαδιαίου προγράμματος. */
data class WeekDay(val day: String, val items: List<ProgramItem>)

/** Τι εκπέμπεται τώρα. Τα πεδία που ο διαχειριστής δεν επιτρέπει έρχονται null. */
data class NowInfo(
    val station: String,
    val onAir: Boolean,
    val id: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val keyText: String?,
    val keyHue: Float,
    val hasCover: Boolean,
    val zone: String?,
    val zoneDesc: String?,
    val zoneIcon: String?,
    val zoneTheme: String?,
    val history: List<Track> = emptyList(),
    val program: List<ProgramItem> = emptyList(),
    val week: List<WeekDay> = emptyList()
)

class ApiException(val code: Int) : Exception("HTTP $code")

object Api {
    private const val UA = "DjApe1-Android/1.0"

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Cache-Control", "no-cache")
        return c
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null

    fun fetchNow(inv: Invite): NowInfo {
        val c = open(inv.now)
        try {
            val code = c.responseCode
            if (code != 200) throw ApiException(code)
            val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            return NowInfo(
                station = j.optString("station", "Dj Ape1"),
                onAir = j.optBoolean("onAir", false),
                id = j.optString("id", ""),
                title = j.str("title"),
                artist = j.str("artist"),
                album = j.str("album")?.ifBlank { null },
                genre = j.str("genre")?.ifBlank { null },
                keyText = j.str("keyText")?.ifBlank { null },
                keyHue = j.optDouble("keyHue", 0.0).toFloat(),
                hasCover = j.optBoolean("cover", false),
                zone = j.str("zone")?.ifBlank { null },
                zoneDesc = j.str("zoneDesc")?.ifBlank { null },
                zoneIcon = j.str("zoneIcon")?.ifBlank { null },
                zoneTheme = j.str("zoneTheme")?.ifBlank { null },
                history = parseHistory(j),
                program = parseProgram(j),
                week = parseWeek(j)
            )
        } finally {
            c.disconnect()
        }
    }

    /** Εβδομαδιαίο πρόγραμμα από το /p/<token> του σταθμού (ζώνες με days, start, end, name, desc). */
    fun fetchWeek(inv: Invite): List<WeekDay> {
        val c = open(inv.program)
        try {
            val code = c.responseCode
            if (code != 200) throw ApiException(code)
            val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            return parseWeek(j)
        } finally {
            c.disconnect()
        }
    }

    private fun JSONObject.firstStr(vararg keys: String): String? {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val v = opt(k)?.toString()?.trim()
                if (!v.isNullOrEmpty()) return v
            }
        }
        return null
    }

    /** Προαιρετικό ιστορικό από τον διακομιστή (πεδία history / recent / last). */
    private fun parseHistory(j: JSONObject): List<Track> {
        val arr = j.optJSONArray("history") ?: j.optJSONArray("recent") ?: j.optJSONArray("last") ?: return emptyList()
        val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i)
            if (o is JSONObject) {
                val t = o.firstStr("title", "name") ?: continue
                out.add(Track(o.firstStr("id") ?: "srv$i$t", t, o.firstStr("artist")))
            } else if (o is String && o.isNotBlank()) {
                out.add(Track("srv$i$o", o.trim(), null))
            }
        }
        return out
    }

    private fun parseItems(arr: org.json.JSONArray): List<ProgramItem> {
        val out = ArrayList<ProgramItem>()
        for (i in 0 until arr.length()) {
            val o = arr.opt(i)
            if (o is JSONObject) {
                val name = o.firstStr("name", "zone", "title", "show") ?: continue
                val from = o.firstStr("from", "start", "begin")
                val to = o.firstStr("to", "end", "until")
                val time = o.firstStr("time") ?: when {
                    from != null && to != null && from == to -> "All day"
                    from != null && to != null -> "$from–$to"
                    else -> from
                }
                out.add(ProgramItem(time, name, o.firstStr("desc", "description", "zoneDesc")))
            } else if (o is String && o.isNotBlank()) {
                out.add(ProgramItem(null, o.trim(), null))
            }
        }
        return out
    }

    /** Βρίσκει πίνακες ζωνών/προγράμματος: schedule / program / zones, και μέσα σε αντικείμενα (π.χ. profile.zones). */
    private fun zoneArrays(j: JSONObject): List<org.json.JSONArray> {
        val out = ArrayList<org.json.JSONArray>()
        fun look(o: JSONObject, depth: Int) {
            for (k in listOf("schedule", "program", "zones", "profile", "weekly", "week")) {
                when (val v = o.opt(k)) {
                    is org.json.JSONArray -> out.add(v)
                    is JSONObject -> if (depth < 2) look(v, depth + 1)
                }
            }
        }
        look(j, 0)
        return out
    }

    /** Προαιρετικό πρόγραμμα από τον διακομιστή. */
    private fun parseProgram(j: JSONObject): List<ProgramItem> {
        val arr = zoneArrays(j).firstOrNull() ?: return emptyList()
        return parseItems(arr)
    }

    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Κανονικοποιεί όνομα ημέρας (mon, Δευτέρα, 1…) σε αγγλικό όνομα· αλλιώς το επιστρέφει όπως είναι. */
    private fun dayName(raw: String): String {
        val r = raw.trim().lowercase()
        DAYS.forEachIndexed { i, d ->
            if (r == d.lowercase() || r == d.take(3).lowercase() || r == (i + 1).toString()) return d
        }
        val el = listOf("δευ", "τρι", "τετ", "πεμ", "παρ", "σαβ", "κυρ")
        el.forEachIndexed { i, p -> if (r.startsWith(p)) return DAYS[i] }
        return raw.trim()
    }

    /**
     * Εβδομαδιαίο πρόγραμμα από τον διακομιστή. Δέχεται:
     *  - weekly / week / weeklyProgram / weeklySchedule ως αντικείμενο {"Monday":[…], …}
     *  - το ίδιο ως πίνακα [{"day":"Monday","items":[…]}, …]
     *  - schedule / program με πεδίο "day" σε κάθε γραμμή (ομαδοποιούνται ανά ημέρα)
     */
    private fun parseWeek(j: JSONObject): List<WeekDay> {
        val keys = listOf("weekly", "week", "weeklyProgram", "weeklySchedule", "weekSchedule")
        val out = ArrayList<WeekDay>()
        for (k in keys) {
            val v = j.opt(k) ?: continue
            if (v is JSONObject) {
                val keysIt = v.keys()
                while (keysIt.hasNext()) {
                    val dk = keysIt.next()
                    val arr = v.optJSONArray(dk) ?: continue
                    out.add(WeekDay(dayName(dk), parseItems(arr)))
                }
            } else if (v is org.json.JSONArray) {
                for (i in 0 until v.length()) {
                    val o = v.optJSONObject(i) ?: continue
                    val d = o.firstStr("day") ?: continue
                    val arr = o.optJSONArray("items") ?: o.optJSONArray("program") ?: o.optJSONArray("schedule")
                        ?: o.optJSONArray("shows") ?: o.optJSONArray("zones") ?: continue
                    out.add(WeekDay(dayName(d), parseItems(arr)))
                }
            }
            if (out.isNotEmpty()) return sortWeek(out)
        }
        // Ζώνες με days = [bool × 7] (Δευ…Κυρ) ή πεδίο day/days ως κείμενο
        val grouped = LinkedHashMap<String, MutableList<ProgramItem>>()
        for (arr in zoneArrays(j)) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.has("enabled") && !o.optBoolean("enabled", true)) continue
                val one = parseItems(org.json.JSONArray().put(o)).firstOrNull() ?: continue
                val names = ArrayList<String>()
                val da = o.optJSONArray("days")
                if (da != null && da.length() == 7 && (0 until 7).all { da.opt(it) is Boolean || da.opt(it) is Number }) {
                    for (d in 0 until 7) {
                        val v = da.opt(d)
                        if (v == true || (v is Number && v.toInt() != 0)) names.add(DAYS[d])
                    }
                } else if (da != null) {
                    for (x in 0 until da.length()) names.add(dayName(da.opt(x).toString()))
                } else {
                    val d = o.firstStr("day") ?: continue
                    d.split(',', '/', ';').map { dayName(it) }.filter { it.isNotBlank() }.forEach { names.add(it) }
                }
                names.forEach { grouped.getOrPut(it) { ArrayList() }.add(one) }
            }
            if (grouped.isNotEmpty()) break
        }
        return sortWeek(grouped.map { WeekDay(it.key, it.value.sortedBy { p -> if (p.time == "All day") "" else p.time ?: "" }) })
    }

    private fun sortWeek(l: List<WeekDay>): List<WeekDay> =
        l.sortedBy { val i = DAYS.indexOf(it.day); if (i < 0) 99 else i }

    /** Επιστρέφει το εξώφυλλο ή null όταν δεν υπάρχει. */
    fun fetchCover(inv: Invite, id: String): Bitmap? {
        val c = open(inv.cover(id))
        try {
            if (c.responseCode != 200) return null
            val bytes = c.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1024) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } finally {
            c.disconnect()
        }
    }
}

/** Κοινή κατάσταση ανάμεσα στην υπηρεσία αναπαραγωγής και την οθόνη (ίδια διεργασία). */
object Hub {
    @Volatile var playing = false
    @Volatile var status = "Ready"
    @Volatile var bad = false
    val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun update(playing: Boolean, status: String, bad: Boolean = false) {
        this.playing = playing; this.status = status; this.bad = bad
        main.post { listeners.forEach { it() } }
    }
}

/** Τοπικό ιστορικό: τα τελευταία κομμάτια που είδε η εφαρμογή (όταν ο διακομιστής δεν στέλνει δικό του). */
object History {
    private const val MAX = 6

    @Synchronized
    fun record(c: android.content.Context, info: NowInfo) {
        if (!info.onAir || info.title.isNullOrBlank() || info.id.isBlank() || info.genre == "Jingle") return
        val list = load(c).toMutableList()
        if (list.firstOrNull()?.id == info.id) return
        list.removeAll { it.id == info.id }
        list.add(0, Track(info.id, info.title, info.artist))
        val arr = org.json.JSONArray()
        list.take(MAX).forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist ?: JSONObject.NULL))
        }
        Prefs.setHistoryJson(c, arr.toString())
    }

    fun load(c: android.content.Context): List<Track> = try {
        val arr = org.json.JSONArray(Prefs.historyJson(c))
        (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            Track(o.optString("id"), o.optString("title"), if (o.isNull("artist")) null else o.optString("artist"))
        }
    } catch (e: Exception) { emptyList() }

    /** Τα 5 κομμάτια που έπαιξαν πριν από το τρέχον. */
    fun last5(c: android.content.Context, info: NowInfo?): List<Track> {
        val src = if (info != null && info.history.isNotEmpty()) info.history else load(c)
        return src.filter { it.id != info?.id && !(info != null && it.title == info.title && it.artist == info.artist) }.take(5)
    }
}
