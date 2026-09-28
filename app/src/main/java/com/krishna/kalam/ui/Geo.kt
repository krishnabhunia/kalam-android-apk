package com.krishna.kalam.ui

/** K48 — pure helpers for place resolution (unit-tested; no Android imports). */
object Geo {
    /** Human-readable candidate label: "Bijapur · Karnataka · IN"; degrades by dropping blanks. */
    fun label(name: String?, admin: String?, country: String?): String {
        val parts = listOfNotNull(
            name?.takeIf { it.isNotBlank() },
            admin?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() })
        return if (parts.isEmpty()) "Unknown place" else parts.joinToString(" \u00b7 ")
    }


    data class PlaceCand(val name: String, val state: String, val country: String,
                         val countryCode: String, val lat: Double, val lon: Double)

    private val SETTLE = setOf("city", "town", "village", "municipality", "hamlet", "suburb")

    private fun block(s: String, open: Int, cap: Int = 6000): String? {
        var d = 0; var i = open
        while (i < s.length && i - open < cap) {
            val c = s[i]
            if (c == '"') { i++
                while (i < s.length) { if (s[i] == '\\') i += 2 else if (s[i] == '"') { i++; break } else i++ }
                continue }
            if (c == '{') d++ else if (c == '}') { d--; if (d == 0) return s.substring(open, i + 1) }
            i++
        }
        return null
    }

    private fun str(src: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(src)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** Tolerant Nominatim jsonv2 parser (K49). settlementsOnly drops districts/POIs; unknown addresstype kept. */
    fun parseNominatim(json: String, settlementsOnly: Boolean): List<PlaceCand> {
        val out = ArrayList<PlaceCand>()
        try {
            val lb = json.indexOf('['); if (lb < 0) return out
            var i = lb + 1; var n = 0
            while (n < 16) {
                val ob = json.indexOf('{', i); if (ob < 0) break
                val item = block(json, ob) ?: break
                i = ob + item.length; n++
                val at = str(item, "addresstype")
                if (settlementsOnly && at != null && at !in SETTLE) continue
                val lat = str(item, "lat")?.toDoubleOrNull() ?: continue
                val lon = str(item, "lon")?.toDoubleOrNull() ?: continue
                val name = str(item, "city") ?: str(item, "town") ?: str(item, "village")
                    ?: str(item, "municipality") ?: str(item, "name")
                    ?: str(item, "display_name")?.substringBefore(',')?.trim() ?: continue
                out.add(PlaceCand(name, str(item, "state") ?: "", str(item, "country") ?: "",
                    (str(item, "country_code") ?: "").lowercase(), lat, lon))
            }
        } catch (t: Throwable) { /* total: bad payload -> empty */ }
        return out
    }

    /** K49 ordering law: India first (states ascending), then countries ascending, states within. */
    fun order(list: List<PlaceCand>): List<PlaceCand> = list.sortedWith(compareBy(
        { it.countryCode != "in" },
        { if (it.countryCode == "in") "" else it.country.lowercase() },
        { it.state.lowercase() },
        { it.name.lowercase() }))


    /** K51 — extract an IANA zone from Open-Meteo ("timezone") or timeapi.io ("timeZone"). Pure. */
    fun parseTimezone(json: String): String? =
        Regex("\"time[zZ]one\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }


    /** K53 — collapsed sun segment: "0611–1911" only when BOTH events exist, else "—". */
    fun sunSegment(rise: String?, set: String?): String =
        if (rise != null && set != null) "$rise–$set" else "—"


    /** K54 — numeric temperature_2m from Open-Meteo current-weather JSON; units entry
     *  ("temperature_2m":"°C") is non-numeric so the first numeric match is the value. */
    fun parseTemperature(json: String): Double? =
        Regex("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)
            ?.groupValues?.get(1)?.toDoubleOrNull()


    /** K70 — generic numeric field from Open-Meteo current JSON (units entries are
     *  non-numeric strings, so the first numeric match is the value — same trick as K54). */
    fun parseNum(json: String, field: String): Double? =
        Regex("\"" + Regex.escape(field) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)
            ?.groupValues?.get(1)?.toDoubleOrNull()

    /** K70 — US AQI band label; null AQI handled by caller. */
    fun aqiBand(aqi: Int): String = when {
        aqi <= 50 -> "Good"; aqi <= 100 -> "Moderate"; aqi <= 150 -> "Sensitive"
        aqi <= 200 -> "Unhealthy"; aqi <= 300 -> "Very Unhealthy"; else -> "Hazardous"
    }

    /** K57 — "Updated 11/Aug/26 1632 hrs" (Locale.ENGLISH pinned so MMM never localizes). */
    fun updatedCaption(epochMin: Long, zone: java.time.ZoneId): String =
        "Updated " + java.time.Instant.ofEpochMilli(epochMin * 60000)
            .atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MMM/yy HHmm", java.util.Locale.ENGLISH)) + " hrs"

    /** 4-decimal dedupe key (~11 m) — same-spot duplicates collapse. */
    fun key(lat: Double, lon: Double): String = "%.4f|%.4f".format(lat, lon)
}


/** K65/K66 — pure clock ordering + grouping helpers (unit-tested). */
object ClockOrder {
    /** Country from a stored region line ("West Bengal · India" → "India"); blank/Saved → null. */
    fun countryOf(region: String): String? {
        val t = region.split("·").map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return null
        return if (t.isEmpty() || t.equals("Saved", true)) null else t
    }

    /** Continent bucket from the IANA zone id — offline, honest, coarse. */
    fun continentOf(tz: String): String? = when (tz.substringBefore('/')) {
        "Asia" -> "Asia"; "Europe" -> "Europe"; "Africa" -> "Africa"
        "America" -> "Americas"; "Australia", "Pacific" -> "Oceania"
        "Atlantic" -> "Atlantic"; "Indian" -> "Indian Ocean"; "Antarctica" -> "Antarctica"
        else -> null
    }

    /** Current UTC offset seconds; bad tz sinks to the end (MAX). */
    fun offsetSec(tz: String): Int = try {
        java.time.ZoneId.of(tz).rules.getOffset(java.time.Instant.now()).totalSeconds
    } catch (t: Throwable) { Int.MAX_VALUE }

    /** K65 5-arg form (natural direction, no metrics) — kept for existing callers/tests. */
    fun <T> sort(list: List<T>, mode: String, name: (T) -> String, region: (T) -> String, tz: (T) -> String): List<T> =
        sort(list, mode, false, name, region, tz) { null }

    /** K65/K75 — stable sort. Invalid entries (bad tz / no country / null metric) sink LAST in added order;
     *  `reversed` flips only the valid segment. Metric natural order = highest first. */
    fun <T> sort(list: List<T>, mode: String, reversed: Boolean, name: (T) -> String, region: (T) -> String,
                 tz: (T) -> String, metric: (T) -> Double?): List<T> {
        if (mode == "ADDED") return list
        val valid: (T) -> Boolean = when (mode) {
            "TIME" -> { c -> offsetSec(tz(c)) != Int.MAX_VALUE }
            "COUNTRY" -> { c -> countryOf(region(c)) != null }
            "TEMP", "AQI", "HUM" -> { c -> metric(c) != null }
            else -> { _ -> true }
        }
        val cmp: Comparator<T> = when (mode) {
            "NAME" -> compareBy { name(it).lowercase() }
            "TIME" -> compareBy { offsetSec(tz(it)) }
            "COUNTRY" -> compareBy({ countryOf(region(it)) != "India" }, { countryOf(region(it)) ?: "\uFFFF" }, { name(it).lowercase() })
            "TEMP", "AQI", "HUM" -> compareByDescending { metric(it) ?: Double.NEGATIVE_INFINITY }
            else -> return list
        }
        val (ok, bad) = list.partition(valid)
        val sorted = ok.sortedWith(cmp)
        return (if (reversed) sorted.reversed() else sorted) + bad
    }

    val METRIC_MODES = setOf("TEMP", "AQI", "HUM")

    /** K75 — does any clock carry the metric this mode sorts on? (gates menu entries + Added fallback) */
    fun <T> hasData(list: List<T>, mode: String, metric: (T) -> Double?): Boolean =
        mode !in METRIC_MODES || list.any { metric(it) != null }

    /** K74 — state = second-last region segment ("West Bengal · India" → "West Bengal"). */
    fun stateOf(region: String): String? {
        val segs = region.split("\u00b7").map { it.trim() }.filter { it.isNotEmpty() }
        return if (segs.size >= 2 && countryOf(region) != null) segs[segs.size - 2] else null
    }

    data class Node<T>(val path: String, val name: String, val level: Int, val items: List<T>, val children: List<Node<T>>) {
        fun all(): List<T> = items + children.flatMap { it.all() }
        val count: Int get() = all().size
    }

    private fun <T> levelsFor(mode: String, region: (T) -> String, tz: (T) -> String): List<Pair<String, (T) -> String?>>? = when (mode) {
        "COUNTRY" -> listOf("country" to { c: T -> countryOf(region(c)) })
        "CONTINENT" -> listOf("continent" to { c: T -> continentOf(tz(c)) })
        "COUNTRY_STATE" -> listOf("country" to { c: T -> countryOf(region(c)) }, "state" to { c: T -> stateOf(region(c)) })
        "CONTINENT_COUNTRY" -> listOf("continent" to { c: T -> continentOf(tz(c)) }, "country" to { c: T -> countryOf(region(c)) })
        "CONTINENT_COUNTRY_STATE" -> listOf("continent" to { c: T -> continentOf(tz(c)) }, "country" to { c: T -> countryOf(region(c)) }, "state" to { c: T -> stateOf(region(c)) })
        else -> null
    }

    /** K74 — hierarchical grouping tree; null = grouping off. Other last; India first at country level. */
    fun <T> tree(list: List<T>, mode: String, region: (T) -> String, tz: (T) -> String): List<Node<T>>? {
        val levels = levelsFor(mode, region, tz) ?: return null
        fun build(items: List<T>, depth: Int, prefix: String): List<Node<T>> {
            val (kind, keyer) = levels[depth]
            val m = LinkedHashMap<String, MutableList<T>>()
            for (c in items) m.getOrPut(keyer(c) ?: "Other") { mutableListOf() }.add(c)
            val order = compareBy<String>({ it == "Other" }, { if (kind == "country") it != "India" else false }, { it })
            return m.keys.sortedWith(order).map { k ->
                val path = if (prefix.isEmpty()) k else prefix + "\u25b8" + k
                if (depth + 1 < levels.size) Node(path, k, depth, emptyList(), build(m[k]!!, depth + 1, path))
                else Node(path, k, depth, m[k]!!, emptyList())
            }
        }
        return build(list, 0, "")
    }

    /** Flat single-level grouping (K66 API) — delegates to tree(); null = grouping off. */
    fun <T> group(list: List<T>, mode: String, region: (T) -> String, tz: (T) -> String): List<Pair<String, List<T>>>? =
        tree(list, mode, region, tz)?.map { it.name to it.all() }
}


/** K71 — group stage cycle + codec. pos: 0=A expanded, 1=B compact, 2=C hidden, 3=B compact. */
object GroupStage {
    const val A = 0; const val B1 = 1; const val C = 2; const val B2 = 3
    private const val PAIR = '\u0001'; private const val KV = '\u0002'

    fun advance(pos: Int): Int = (pos + 1) % 4
    /** Visual stage for a cycle position: 0→expanded, 1/3→compact, 2→hidden. */
    fun visual(pos: Int): Int = if (pos == 2) 2 else if (pos == 0) 0 else 1
    fun glyph(pos: Int): String = when (visual(pos)) { 0 -> "\u25be\u25be"; 1 -> "\u25be"; else -> "\u25b8" }

    fun decode(raw: String): Map<String, Int> =
        if (raw.isBlank()) emptyMap() else raw.split(PAIR).mapNotNull { pr ->
            val i = pr.indexOf(KV)
            if (i <= 0) null else pr.substring(0, i) to (pr.substring(i + 1).toIntOrNull()?.takeIf { it in 0..3 } ?: return@mapNotNull null)
        }.toMap()

    fun encode(m: Map<String, Int>): String =
        m.entries.joinToString(PAIR.toString()) { "${it.key}$KV${it.value}" }
}
